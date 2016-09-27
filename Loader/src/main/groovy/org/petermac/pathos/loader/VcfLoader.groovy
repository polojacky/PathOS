/*
 * Copyright (c) 2016. PathOS Variant Curation System. All rights reserved.
 *
 * Organisation: Peter MacCallum Cancer Centre
 * Author: Kenneth Doig
 */

package org.petermac.pathos.loader

import groovy.util.logging.Log4j
import org.apache.log4j.Level
import org.apache.log4j.Logger
import org.codehaus.groovy.runtime.StackTraceUtils
import org.hibernate.Session
import org.petermac.annotate.*
import org.petermac.pathos.curate.SeqVariant
import org.petermac.pathos.curate.VarFilterService
import org.petermac.pathos.pipeline.HGVS
import org.petermac.pathos.pipeline.Mutalyzer
import org.petermac.pathos.pipeline.MutalyzerUtil
import org.petermac.pathos.pipeline.NormaliseVcf
import org.petermac.util.DbConnect
import org.petermac.util.FileUtil
import org.petermac.util.Locator
import org.petermac.util.MysqlCommand
import org.petermac.util.Tsv
import org.petermac.util.Vcf2Tsv
import org.springframework.context.ApplicationContext
import org.springframework.context.support.ClassPathXmlApplicationContext

/**
 * Load a list of VCF files into PathOS
 * Normalise and Annotate each VCF file a add new and unique variants into an annotation cache
 *
 * Author:  Kenneth Doig
 * Date:    4-Sep-16
 */

@Log4j
class VcfLoader
{

    Locator loc = Locator.instance

    /**
     * main method for CLI execution
     *
     * @param args
     */
    static void main( args )
    {
        //	Collect and parse command line args
        //
        def cli = new CliBuilder(   usage: "VcfLoader [options] in.vcf ...",
                                    header: '\nAvailable options (use -h for help):\n',
                                    footer: '\nNormalise, annotate, cache and load a set of VCF files\n')

        //	Options to command
        //
        cli.with
        {
            h( longOpt: 'help',		    'This help message' )
            d( longOpt: 'debug',		'Turn on debugging' )
            f( longOpt: 'filter',		'Apply filter flags to variants' )
            r( longOpt: 'rdb',          args: 1, required: true, 'RDB to use' )
            q( longOpt: 'seqrun',       args: 1, required: true, 'Seqrun of VCF files' )
            s( longOpt: 'datasource',   args: 1, 'Comma separated list of datasources to use for annotation [vep,annovar]' )
            c( longOpt: 'columns',      args: 1, 'File of columns to output' )
            p( longOpt: 'panel',        args: 1, 'Panel name (must exist in PathOS)' )
            e( longOpt: 'errors',       args: 1, 'File name for error records' )
        }

        def opt = cli.parse( args )
        if ( ! opt ) return

        List argin = opt.arguments()
        if ( opt.h || argin.size() < 1 )
        {
            cli.usage()
            return
        }

        //  Debug ?
        //
        if ( opt.debug ) Logger.getRootLogger().setLevel(Level.DEBUG)
        log.debug( "Debugging turned on!" )

        //  Validate data sources
        //
        List dss = [ 'vep', 'annovar' ]
        if ( opt.datasource )
            dss = (opt.datasource as String).tokenize(',')

        //  Open files
        //
        List<File> vcfFiles = []
        for ( inf in argin )
        {
            def infile = new File( inf as String )
            if ( ! infile.exists())
            {
                log.fatal( "File ${infile.name} doesn't exist")
                continue
            }

            if ( infile.isFile())
                vcfFiles << infile
        }

        if ( ! vcfFiles)
        {
            log.fatal( "No data files to process")
            return
        }

        //  Optional list of columns to output
        //
        File colsf = null
        if ( opt.columns )
        {
            colsf = new File( opt.columns as String)
            if ( ! colsf.exists())
            {
                log.error( "File ${opt.columns} doesn't exist")
                return
            }
        }

        //  Test Mutalyzer is available
        //
        if ( ! (new Mutalyzer()).ping())
        {
            log.fatal( "Can't connect to mutalyzer.nl server")
            System.exit(1)
        }

        //  Open error file and zero it if required
        //
        File errFile = null
        if ( opt.errors )
        {
            errFile = new File( opt.errors as String )
            errFile.delete()
        }

        //  Perform data load
        //
        log.info( "Start VcfLoader " + (args.size() > 10 ? args[0..10] + "..." : args))

        //  Process VCFs
        //
        int nmut = new VcfLoader().loadVcf( vcfFiles, opt.seqrun, opt.panel ?: 'NoPanel', opt.rdb, dss, colsf, errFile, opt.filter )

        log.info( "Done: processed ${vcfFiles.size()} files into ${opt.rdb}" )
    }

    /**
     * Load VCF files
     *
     * @param   vcfs    List of VCF Files
     * @param   seqrun  Sequencing run name
     * @param   panel   Sequencing panel name
     * @param   rdb     Database name to populate
     * @param   dss     List of annotation datasources
     * @param   vcfcols Columns to match in VCF file
     * @param   errFile Error output file
     * @param   filter  Apply filtering
     * @return          Rows loaded
     */
    int loadVcf( List<File> vcfs, String seqrun, String panel, String rdb, List dss, File vcfcols, File errFile, boolean filter )
    {
        //  Normalise all VCFs
        //
        List<File> normVcfs = normaliseVcfs( vcfs, rdb, false )

        //  Add VCFs to annotation cache
        //
        int nmut = new Annotator().annotateVcf( normVcfs, rdb, dss, errFile )

        //  Create temporary TSV file for all variants
        //
        File tsv = new File( "vcfs.tsv" )
        tsv.delete()

        //  Create an aggregate TSV file of all variants
        //
        List<Map> sammap = tsvVcfs( vcfs, tsv, seqrun, panel, vcfcols )

        //  Load Seqrun and Sample table into RDB
        //
        loadSeqrun( sammap, rdb )

        //  Load database with RDB tables mp_vcf
        //
        vcfLoad( tsv, 'mp_vcf', rdb )

        //  Populate GORM database from RDB
        //
        int vars = loadGorm( rdb )
        log.info( "Loaded ${vars} variants into GORM db from ${tsv}" )

        //  Apply filtering to variants
        //
        if ( filter )
        {
            SeqVariant.withSession
            {
                Session session ->
                    def vfs = new VarFilterService()
                    int cnt = vfs.applyFilter( session, false )                         //  Filter added SeqVariants
                    log.info( "Set Filter for ${cnt} Variants")
            }
        }

        return vars
    }

    /**
     * Load GORM db from rdb tables
     *
     * @param dbname
     * @return
     */
    static int loadGorm( String dbname )
    {
        def dbl = new DbLoader()

        //  Load HGVS transcripts
        //
        def hg = new HGVS( dbname )

        //  Annotation object for DB
        //
        def vds = new VarDataSource( dbname )
        def db  = new DbConnect( dbname )
        dbl.sql = db.sql()

        //  Load stand-alone Hibernate context - Database JDBC is embedded in <schema>_loaderContext.xml
        //
        ApplicationContext context = new ClassPathXmlApplicationContext( db.hibernateXml)

        //  Add the Seqrun
        //
        int nrow = dbl.addSeqrun( true )
        log.info( "Added ${nrow} Seqrun")

        //  Add the Panels/Assays
        //
        nrow = dbl.addPanels( true )
        log.info( "Added ${nrow} Panels")

        //  Add the SeqSamples
        //
        nrow = dbl.addSeqSamples( true )
        log.info( "Added ${nrow} SeqSamples")

        //  Add the SeqVariants
        //
        nrow = dbl.addSeqVariants( vds, true )
        log.info( "Added ${nrow} SeqVariants")

        return nrow
    }

    /**
     * Normalise each VCF file
     *
     * @param vcfs      List of VCF Files
     * @param rdb       DN name of annotation cache
     * @param nocache   Use cache flag
     * @return          List of normalised VCF Files
     */
    static List<File> normaliseVcfs( List<File> vcfs, String rdb, boolean nocache)
    {
        List<File> normVcfs = []
        def nvcf = new NormaliseVcf()

        for ( vcf in vcfs )
        {
            log.info( "Normalising VCF ${vcf.name}" )
            File normVcf = FileUtil.tmpFixedFile()
            try
            {
                int nmut = nvcf.normaliseVcf( vcf, normVcf, rdb, nocache )
            }
            catch( Exception e )
            {
                StackTraceUtils.sanitize(e).printStackTrace()
                log.fatal( "Exiting: Couldn't normalise file ${vcf} " + e.toString())
                System.exit(1)
            }

            normVcfs << normVcf
        }

        return normVcfs
    }

    /**
     * Create a TSV containing each VCF file variants
     *
     * @param vcfs      List of VCF Files
     * @param vcfcols   Column names of TSV file
     * @return          List of samples
     */
    static List<Map> tsvVcfs( List<File> vcfs, File tsv, String seqrun, String panel, File vcfcols )
    {
        int     nl     = 0
        List    sammap = []

        //  Process each VCF file into a TSV
        //
        for ( vcf in vcfs )
        {
            String sample = vcf.name
            def match = ( sample =~ /([^\.]+)/ )
            if ( match.count ) sample = match[0][1]

            nl += Vcf2Tsv.vcf2Tsv( vcf, tsv, sample, seqrun, panel, vcfcols, false )

            sammap << [ seqrun: seqrun, sample: sample, panel: panel, pipeline: 'VcfLoader' ]
        }

        return sammap
    }

    /**
     * Create a file of Seqrun/Sample meta data
     *
     * @param   sammap  List of Maps of runs/samples/panels
     * @param   rdb     DB name to load into
     * @return
     */
    Boolean loadSeqrun( List<Map> sammap, String rdb )
    {
        //  Load database with RDB tables mp_vcf, mp_seqrun
        //
        def srf = new File( "mp_seqrun.tsv")
        srf.delete()

        //  Create mp_seqrun.tsv file
        //
        LoadPathOS.createSeqrun( sammap, srf, false )

        def res = vcfLoad( srf, 'mp_seqrun', rdb )
        log.info( "Loaded mp_seqrun from ${srf} = ${res}" )

        return true
    }

    /**
     * Load TSV file into DB
     *
     * @param tsv       File of TSV file
     * @param table     Name of RDB table to populate
     * @param dbname    Name of db to populate
     * @return          true if succeeded
     */
    Boolean vcfLoad( File tsv, String table, String dbname )
    {
        log.info( "Loading from ${tsv} to: ${dbname}")

        //  Set etc dir
        //
        def sqlDir = "${loc.pathos_home}/ETL/Tables/"

        //  Find table create script
        //
        def tfile = new File( sqlDir, table + '.sql' )
        if ( ! tfile.exists())
        {
            log.fatal( "Table create script doesn't exist: ${tfile.path}" )
            System.exit(1)
        }

        //  SQL to drop and create table
        //
        def dml =   """
                    drop table if exists ${table};
                    source ${tfile.path};
                    """

        //  Find table raw data
        //
        if ( ! tsv.exists())
        {
            log.fatal( "Table data doesn't exist: ${tsv.path}" )
            System.exit(1)
        }

        //  SQL to load in data
        //
        dml +=  """
                    load data local infile '${tsv.path}' into table ${table};
                    show warnings;
                    """

        //  Drop and load table
        //
        log.info( "Loading Table ${table} ...")
        new MysqlCommand( dbname ).run( dml )

        //  Look for optional index file
        //
        def ifile = new File( sqlDir , table + '.idx.sql' )
        if ( ifile.exists())
        {
            dml =   """
                    source ${ifile.path};
                    show warnings;
                    """

            log.info( "Loading Index(es) for ${table} ...")
            new MysqlCommand( dbname ).run( dml )
        }

        return true
    }
}