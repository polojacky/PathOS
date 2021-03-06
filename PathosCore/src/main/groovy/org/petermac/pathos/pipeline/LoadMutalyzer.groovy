/*
 * Copyright (c) 2014. PathOS Variant Curation System. All rights reserved.
 *
 * Organisation: Peter MacCallum Cancer Centre
 * Author: doig ken
 */

package org.petermac.pathos.pipeline

import groovy.util.logging.Log4j
import org.apache.log4j.Level
import org.apache.log4j.Logger

/**
 * Extract variant nomenclature from Mutalyser web site
 *
 * Author:  Kenneth Doig
 * Date:    22-Aug-14
 */

@Log4j
class LoadMutalyzer
{
    static def mu = new MutalyzerUtil()

    /**
     * main method for CLI execution
     *
     * @param args
     */
    static void main( args )
    {
        //	Collect and parse command line args
        //
        def cli = new CliBuilder(   usage: "LoadMutalyzer [options]",
                                    header: '\nAvailable options (use -h for help):\n',
                                    footer: '\nExtract variant nomelclature from Mutalyser web site\n')

        //	Options to command
        //
        cli.with
        {
            h( longOpt: 'help',		    'this help message' )
            d( longOpt: 'debug',		'turn on debugging' )
            o( longOpt: 'output',       args: 1, 'output file [mp_mutalyser.tsv]' )
            i( longOpt: 'input',        args: 1, required: true, 'variant file' )
            t( longOpt: 'type',         args: 1, 'type of request (check|convert|vcf|tsv) [check]' )
            r( longOpt: 'rdb',          args: 1, required: true, 'cache RDB [pa_local]' )
        }

        def opt = cli.parse( args )
        if ( ! opt ) return
        if ( opt.h )
        {
            cli.usage()
            return
        }

        //  Debug ?
        //
        if ( opt.debug ) Logger.getRootLogger().setLevel(Level.DEBUG)
        log.debug( "Debugging turned on!" )

        //  Open files
        //
        def ofile = new File( opt.output ?: 'mp_mutalyser.tsv')
        if ( ofile.exists()) ofile.delete()

        def infile = new File( opt.input as String )
        if ( ! infile.exists())
        {
            log.fatal( "File ${infile.name} doesn't exist")
            return
        }

        //  Test Mutalyzer is available
        //
        try
        {
            if ( ! Mutalyzer.ping())
            {
                log.fatal( "Couldn't connect to Mutalyzer web site")
                return
            }
        }
        catch( Exception bad)
        {
            log.error( "HTTP Connect error: " + bad.toString())
            return
        }

        //  Perform data load
        //
        def nmut = 0
        log.info( "Start LoadMutalyzer " + args )

        //  Select the processing type
        //
        switch ( opt.type )
        {
            case 'convert':
                nmut = convertMut(infile, ofile)
                break
            case 'vcf':
                nmut = mu.convertVcf(infile, ofile, opt.rdb ?: 'pa_local', false )
                break
            case 'tsv':
                nmut = convertTsv(infile, ofile, opt.rdb ?: 'pa_local' )
                break
            default:
                nmut = checkMut(infile, ofile)
        }

        log.info( "Done: processed ${nmut} mutations into ${ofile}" )
    }

    /**
     * Run mutalyser batch extract to convert VCF variants
     *
     * @param   infile      VCF File
     * @param   ofile       Output TSV file
     * @return              Number of variants processed
     */
    static int convertTsv( File infile, File ofile, String cacheDB )
    {
        //  Read in variants as HGVSg
        //
        //  Read in test muts
        //
        List hgs = infile.readLines()

        //  Process all variants
        //
        def varmods = mu.cacheVariants( hgs, cacheDB )

        //  Output table of variants
        //
        tsvOutput( varmods, ofile )

        log.info( "convertTsv(${infile.name}): In ${hgs.size()} Out ${varmods.size()}")

        return hgs.size()
    }

    /**
     * Output the detailed variant results
     *
     * @param vars      List of Maps of variants
     * @param ofile     Output file
     */
    static void tsvOutput( List<Map> vars, File ofile )
    {
        //        1  #Variant    chr3:g.178936091G>A
        //        2  Status
        //        3  Error
        //        4  FilterTS    NM_006218.2:c.1633G>A
        //        5  Transcript  NM_006218.2
        //        6  Refseq      NM_006218.2:c.1633G>A
        //        7  LRG         LRG_310t1:c.1633G>A
        //        8  Gene        PIK3CA
        //        9  HGVSg       NC_000003.11:g.178936091G>A
        //        10 HGVSn       g.74781G>A
        //        11 HGVSc       NM_006218.2:c.1633G>A
        //        12 HGVSp       NP_006209.2:p.(Glu545Lys)

        //  delete output file
        //
        if ( ofile.exists()) ofile.delete()

        //  Create output file
        //
        ofile << """##   Generated by LoadMutalyzer
##
#Variant\tStatus\tError\tFilterTS\tTranscript\tRefseq\tLRG\tGene\tHGVSg\tHGVSn\tHGVSc\tHGVSp
"""
        for ( var in vars )
        {
            List row =  [
                        var.variant,                                // HGVSg in chrnn format
                        var.status ?: (var.error ? 'ERROR' : ''),   // ERROR|RENAMED|<blank>
                        var.error,                                  // error msg or original hgvsg
                        var.filterts    ?: '',
                        var.transcript  ?: '',
                        var.refseq      ?: '',
                        var.lrg         ?: '',
                        var.gene        ?: '',
                        var.hgvsg       ?: '',                      // HGVSg in refseq format
                        var.hgvsn       ?: '',
                        var.hgvsc       ?: '',
                        var.hgvsp       ?: ''
                        ]

            ofile << row.join('\t') + '\n'
        }
    }

    /**
     * Run mutalyser batch extract to check HGVSc variants
     *
     * @param infile    File of HGVSc variants, one per line
     * @param ofile     Output TSV file [Variant	Status	Error	Corrected	Transcript	Gene	HGVSc	HGVSp]
     * @return          Number of variants processed
     */
    static int checkMut( File infile, File ofile )
    {
        assert infile.exists()

        //  Read in test muts
        //
        List ml = infile.readLines()

        //  Perform validation
        //
        def mutl = Mutalyzer.batchNameChecker(ml)

        //  Create output file
        //
        ofile << """##   Generated by LoadMutalyzer
##
#Variant\tStatus\tError\tCorrected\tTranscript\tGene\tHGVSc\tHGVSp
"""

        def validMut = 0
        def errors   = 0
        for ( Map mut in mutl )
        {
            def status = 'OK'
            def result = mut.hgvsc ? mut.transcript + ':' + mut.hgvsc : mut.in
            if ( result != mut.in ) status = 'EDIT'
            if ( mut.error )
            {
                ++errors
                status = 'ERROR'
                result = ''
            }
            else
                ++validMut

            List outRow = [ mut.in, status, mut.error, result, mut.transcript ?:'', mut.gene ?:'', mut.hgvsc ?:'', mut.hgvsp ?:'' ]
            ofile << outRow.join('\t') + '\n'
        }

        log.info( "In ${ml.size()} Out ${mutl.size()} OK ${validMut} Errors ${errors}")

        return ml.size()
    }

    /**
     * Run mutalyser batch extract to convert the position of HGVSg/c variants
     *
     * @param infile    File of HGVSc variants, one per line
     * @param ofile     Output [HGVSg	HGVSc   Error]
     * @return          Number of variants processed
     */
    static int convertMut( File infile, File ofile )
    {
        assert infile.exists()

        //  Read in test muts
        //
        List ml = infile.readLines()

        //  Perform validation
        //
        List<Map> mutl = Mutalyzer.batchPositionConverter(ml)

        //  Create output file
        //
        ofile << """##   Generated by LoadMutalyzer
##
#Variant\tError\tHGVSg\tTranscripts
"""

        def errors = 0
        for ( mut in mutl )
        {
            if ( mut.error ) ++errors
            List outRow = [ mut.variant, mut.error, HGVS.normalise(mut.hgvsg) ]
            outRow << mut.transcripts.split(',')
            ofile << outRow.flatten().join('\t') + '\n'
        }

        log.info( "In ${ml.size()} Out ${mutl.size()} Errors ${errors}")

        return ml.size()
    }
}