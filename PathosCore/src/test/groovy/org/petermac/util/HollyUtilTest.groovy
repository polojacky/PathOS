/*
 * Copyright (c) 2015. PathOS Variant Curation System. All rights reserved.
 *
 * Organisation: Peter MacCallum Cancer Centre
 * Author: doig ken
 */

package org.petermac.util

/**
 * Created for PathOS.
 *
 * Description:
 *
 *
 *
 * User: doig ken
 * Date: 29/10/2015
 * Time: 12:33 PM
 */
class HollyUtilTest extends GroovyTestCase
{
    HollyUtil hu

    /**
     * TESTING Constructor
     */
    void setUp()
    {
        hu = new HollyUtil()
        assert hu instanceof HollyUtil : "[T E S T]: Cannot create class"
    }

    /**
     * TESTING static Map getSample( String sample )
     */
    void testGetSample()
    {
        Map res = hu.getSample( '15K2876' )
        assert res.sample == '15K2876' :"[T E S T ]: hu.getSample( '15K2876' ) is not working"

    }
    /**
     * TESTING static Map getSample( String sample )
     * with more samples
     */
    void testAll()
    {
        List<String> samples = ['14M3434',
                '15K2862',
                '14MTEST',
                '15K2869',
                '15k2881',
                '15K2880',
                '15K2879',
                '15K2831',
                '15K2875',
                '15K2874',
                '15K2873',
                '15K2823',
                '15K2824',
                '15K2878',
                '15K2877',
                '15K2876',
                '15K3188',
                '15K3179',
                '15K3180',
                '15K3178',
                '15K3171',
                '15K3176',
                '15K3175',
                '15K3182',
                '15K3186',
                '15K3185',
                '15K3184',
                '15K3189',
                '15K3206',
                '15K3183',
                '15K3187',
                '15K3181',
                '15K3219',
                '15K3220',
                '15K3221',
                '15K3222',
                '15K3223',
                '15K3207',
                '15K3208',
                '15K3209',
                '15K3218',
                '15K3173',
                '15K3174',
                '15K3167',
                '15K3196',
                '15K3166',
                '15K3224',
                '15K3227',
                '15K3210',
                '153225',
                '15K3226',
                '15K3168',
                '15K3170',
                '15K3172',
                '15K3169',
                '15K3217',
                '15K3213',
                '15K3215',
                '15K3214',
                '15K3216',
                '15K3229',
                '15K3193',
                '15K3197',
                '15K3191',
                '15K3203',
                '15K3201',
                '15K3205',
                '15K3200',
                '15K3198',
                '15K3199',
                '15K3235',
                '15K3228',
                '15K1859',
                '15K3247',
                '15K3246',
                '15K3248',
                '15K3245',
                '15K3231',
                '15K3230',
                '15K3239',
                '15K3240',
                '15K3236',
                '15K3237',
                '15K3238',
                '15K3234',
                '15K3241',
                '15K3233',
                '15K3249',
                '15K3190',
                '15K3204',
                '15K3194',
                '15K3195',
                '15K3202',
                '15K3250',
                '15K3251',
                '15K3253',
                '15K3254',
                '15K3255',
                '15K3263',
                '15K3265',
                '15K3252',
                'test',
                '15K3266',
                '15K3292',
                '15K3294',
                '15K3297',
                '15K3299',
                '15K3295',
                '15K3296',
                '15K3298',
                '15K3293',
                '15K3346',
                '15K3403',
                '15K3404',
                '15K3414',
                '15K3405',
                '15K3400',
                '15K3408',
                '15K3406',
                '15K3407',
                '15K3401',
                '15K3468',
                '15K3469',
                '15K3470',
                '15K3430',
                '15K3431',
                '15K3467',
                '15K3432',
                '15K3429',
                '15K3464',
                '15K3466',
                '15K3465',
                '15K3478',
                '15K3471',
                '15K3476',
                '15K3477',
                '15K3472',
                '15K3473',
                '15K3480',
                '15K3479',
                '15K3475',
                '15K3474',
                '15K3490',
                '15K3498',
                '15K3495',
                '15K3496',
                '15k3497',
                '15k3499',
                '15k3500',
                '15k3505',
                '13MTEST',
                '15K3501',
                '15K3502',
                '15K3654',
                '15K3634',
                '15K3638',
                '15K3640',
                '15K3632',
                '15K3636',
                '15K3637',
                '15K3641',
                '15K3639',
                '15K3633',
                '15K3631',
                '15K3670',
                '15K3671',
                '15K3672',
                '15K3673',
                '15K3674',
                '15K3688',
                '15K3689',
                '15K2610',
                '15K2887',
                '15K3031',
                '15K2871',
                '15K2890',
                '15K2882',
                '15K2885',
                '15K2886',
                '15K3007',
                '15K3006',
                '15K3004',
                '15K3005',
                '15K2883',
                '15K2872',
                '15K0743',
                '15K0877',
                '15K4147',
                '15K4148',
                '15K4151',
                '15K4152',
                '15K4153',
                '15K4146',
                '15K4154',
                '15K4156',
                '15K3225',
                '15K4161',
                '15K4150',
                '15K4165',
                '15K4163',
                '15K4164',
                '15k4192',
                '15K4193',
                '15K4194',
                '15K4195',
                '15K4196',
                '15K4162',
                '15K4199',
                '15K4200',
                '15K4212',
                '15K4198',
                '15K4197',
                '15K4213',
                '15K0874',
                '15K0878',
                '15K0884',
                '15K2650',
                '15K2657',
                '15K1302',
                '15K1301',
                '15K0888',
                '15K1299',
                '15K1300',
                '15K0828',
                '15K4274',
                '15K4273',
                '15K4275',
                '15K0885',
                '15K2649',
                '15K4376',
                '15K4374',
                '15K4375',
                '15k4377'
                ]

        for ( sample in samples )
        {
            Map res = hu.getSample( sample )

            if ( ! res )
            {
                continue
            }
            assert res.sample == sample : "[T E  S T]: Sample ${sample} is invalid"

        }
    }
}
