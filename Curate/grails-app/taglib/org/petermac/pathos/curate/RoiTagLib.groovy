/*
 * Copyright (c) 2014. PathOS Variant Curation System. All rights reserved.
 *
 * Organisation: Peter MacCallum Cancer Centre
 * Author: seleznev andrei
 */

package org.petermac.pathos.curate

/**
 * Created by seleznev andrei on 17/11/2014.
 */
class RoiTagLib
{
    static namespace = 'roi'

    def ampliconRoiService

    def roiList =
    {
        attr ->
        def sample   = attr.sample
        def testset  = attr.testset

        List<Map> allroi = ampliconRoiService.roiCoverage( sample, testset )

        // if we don't find anything for our template, show all
        //
        if (allroi.isEmpty() )
        {
            allroi = ampliconRoiService.roiCoverage( sample, null )
        }

        String output
        if( ! allroi.isEmpty())
        {
            output = "<table border=\"1\"><tbody><tr><th>ROI Name</th><th>ROI Coverage</th></tr>"
            for (roi in allroi)
            {
                roi.getClass()
                output = output + "<tr>"
                output = output + "<td>${roi.name}</td><td>${roi.coverage}</td>"
                output = output + "</tr>"
            }
            output = "${output}</tbody></table>"
        }
        else
        {
            output = "No ROI for this panel"
        }

        out << "${output}"
    }
}
