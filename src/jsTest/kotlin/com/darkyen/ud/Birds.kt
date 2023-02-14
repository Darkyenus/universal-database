package com.darkyen.ud

import com.darkyen.ucbor.CborReadSingle
import com.darkyen.ucbor.CborSerializer
import com.darkyen.ucbor.CborSerializers
import com.darkyen.ucbor.CborWrite

// Testing bird dataset
// https://query.wikidata.org/#SELECT%20%3FbirdlifeId%20%3FbirdLabel%20%3FconservationStatusLabel%20%3FtaxonWingspan%20%3FbirdMass%0AWHERE%0A%7B%0A%20%20%20%20%3Fbird%20wdt%3AP31%20wd%3AQ16521.%0A%20%20%20%20%3Fbird%20wdt%3AP141%20%3FconservationStatus.%0A%20%20%20%20%3Fbird%20p%3AP2050%20%3FtaxonWingspanProperty.%0A%20%20%20%20%3FtaxonWingspanProperty%20psn%3AP2050%20%3FtaxonWingspanNormalized.%0A%20%20%3FtaxonWingspanNormalized%20wikibase%3AquantityLowerBound%20%3FtaxonWingspan.%0A%20%20%20%20%3Fbird%20wdt%3AP5257%20%3FbirdlifeId%20.%0A%20%20%20%20%3Fbird%20p%3AP2067%20%3FbirdMassProperty%20.%0A%20%20%20%20%3FbirdMassProperty%20pq%3AP642%20wd%3AQ78101716.%0A%20%20%3FbirdMassProperty%20pq%3AP21%20wd%3AQ43445.%0A%20%20%3FbirdMassProperty%20psn%3AP2067%2Fwikibase%3AquantityAmount%20%3FbirdMass.%0A%20%20%0A%20%20%20%20SERVICE%20wikibase%3Alabel%20%7B%20bd%3AserviceParam%20wikibase%3Alanguage%20%22en%22%20%7D%0A%7D%0AORDER%20BY%20%3FbirdLabel%0ALIMIT%201000

enum class ConservationStatus {
    LeastConcern,
    NearThreatened,
    Vulnerable,
    Endangered,
    CriticallyEndangered,
    ExtinctInTheWild,
    ExtinctSpecies,
    DataDeficient;

    companion object {
        private val byString = values().associateBy { it.name.lowercase() }
        fun byName(name: String): ConservationStatus {
            return byString[name.lowercase().replace(" ", "")]!!
        }

        val SERIALIZER = CborSerializers.EnumSerializer(values())
        val KEY_SERIALIZER = EnumKeySerializer(values())
    }
}
class Bird(val birdlifeId:Long, val name: String, val conservationStatus:ConservationStatus, val wingspanM:Float, val massKG:Float)

val BIRD_SERIALIZER = object : CborSerializer<Bird> {
    override fun CborReadSingle.deserialize(): Bird {
        return obj {
            Bird(
                fieldIntOrZero(0),
                fieldString(1),
                field(2, ConservationStatus.SERIALIZER),
                fieldFloat32OrZero(3),
                fieldFloat32OrZero(4),
            )
        }
    }

    override fun CborWrite.serialize(value: Bird) {
        obj {
            field(0, value.birdlifeId)
            field(1, value.name)
            field(2, value.conservationStatus, ConservationStatus.SERIALIZER)
            field(3, value.wingspanM)
            field(4, value.massKG)
        }
    }

}

private val birdsCSV = """22695541,Accipiter haplochrous,Near Threatened,0.58,0.255
22695619,Accipiter ovampensis,Least Concern,0.6,0.24
22695558,Accipiter poliocephalus,Least Concern,0.56,0.3
22695565,Accipiter superciliosus,Least Concern,0.38,0.125
22696466,African Hobby,Least Concern,0.6,0.2
22680174,American Black Duck,Least Concern,0.85,1.111
22697611,American White Pelican,Least Concern,2.4,4.97
22696437,Amur Falcon,Least Concern,0.63,0.15
22732019,Augur Buzzard,Least Concern,1.2,1.2
22692680,Australian Crake,Least Concern,0.27,0.057
22696473,Australian Hobby,Least Concern,0.7,0.282
22696782,Australian Pied Cormorant,Least Concern,1.1,1.715
22680011,Australian Shelduck,Least Concern,0.9,1.291
22696103,Ayres's Hawk-Eagle,Least Concern,1.1,1.014
22680384,Baer's Pochard,Critically Endangered,0.7,0.68
22679893,Bar-headed Goose,Least Concern,1.4,2.23
22680459,Barrow's Goldeneye,Least Concern,0.67,0.73
22696457,Bat Falcon,Least Concern,0.51,0.202
22696457,Bat Falcon,Least Concern,0.65,0.202
22696484,Black Falcon,Least Concern,0.96,0.7976
22679843,Black Swan,Least Concern,1.6,5.1
22693002,Black-bellied Sandgrouse,Least Concern,0.7,0.383
22695757,Black-faced Hawk,Least Concern,0.65,0.355
22693567,Black-faced Sheathbill,Least Concern,0.74,0.551
22693943,Black-fronted Dotterel,Least Concern,0.33,0.0315
22694289,Black-tailed Gull,Least Concern,1.2,0.522
22696354,Brown Falcon,Least Concern,0.9,0.6814
22695527,Brown Goshawk,Least Concern,0.7,0.59
22733989,Brown Pelican,Least Concern,2,3.174
22680462,Bufflehead,Least Concern,0.53,0.367
22698182,Buller's Shearwater,Vulnerable,0.96,0.3065
22694321,California Gull,Least Concern,1.22,0.71
22728349,Campbell Albatross,Vulnerable,2.1,2.675
22735929,Caspian Gull,Least Concern,1.37,1.033
22678664,Caspian Snowcock,Least Concern,0.95,2.07
22694524,Caspian Tern,Least Concern,1.27,0.588
22693137,Chatham Snipe,Vulnerable,0.28,0.0854
22692990,Chestnut-bellied Sandgrouse,Least Concern,0.48,0.19
22697742,Christmas Island Frigatebrid,Vulnerable,2.1,1.55
22678691,Chukar Partridge,Least Concern,0.49,0.565
22696291,Collared Forest Falcon,Least Concern,0.72,0.8
22693540,Comb-crested Jacana,Least Concern,0.39,0.13
22724320,Common Grackle,Near Threatened,0.39,0.0922
22696201,Crowned Eagle,Near Threatened,1.5,3.937
22695425,Dark Chanting Goshawk,Least Concern,0.86,0.8465
22705935,Daurian Jackdaw,Least Concern,0.67,0.191
22695060,Double-toothed Kite,Least Concern,0.6,0.21
22692880,Dusky Moorhen,Least Concern,0.55,0.493
22695431,Eastern Chanting Goshawk,Least Concern,0.96,0.735
22693199,Eastern Curlew,Endangered,0.97,0.8079
22680306,Eaton's Pintail,Vulnerable,0.65,0.441
22688927,Eurasian Eagle-owl,Least Concern,1.6,2.992
22689194,Eurasian Pygmy Owlet,Least Concern,0.34,0.073
22680373,Ferruginous Duck,Near Threatened,0.63,0.52
22695445,Gabar Goshawk,Least Concern,0.56,0.2
22694334,Glaucous-winged Gull,Least Concern,1.32,0.9461
22696998,Great Blue Heron,Least Concern,1.7,2.11
22693359,Great Knot,Endangered,0.56,0.174
22680086,Green Pygmy Goose,Least Concern,0.48,0.295
22727714,Grey Goshawk,Least Concern,0.7,0.72
22695998,Harpia harpyja,Vulnerable,1.8,7.5
22693154,Hudsonian Godwit,Least Concern,0.67,0.289
22696048,Imperial Eagle,Vulnerable,1.8,3.845
22693936,Inland Dotterel,Least Concern,0.43,0.0882
22695987,Jackal Buzzard,Least Concern,1.27,1.42
22693078,Latham’s Snipe,Near Threatened,0.48,0.1604
22696267,Laughing Falcon,Least Concern,0.75,0.695
22680402,Lesser Scaup,Least Concern,0.68,0.748
22695039,Letter-winged Kite,Near Threatened,0.84,0.343
22696788,Little Black Cormorant,Least Concern,0.95,0.9
22696944,Little Blue Heron,Least Concern,0.95,0.315
22693165,Little Curlew,Least Concern,0.68,0.182
22734332,Little Eagle,Least Concern,1,0.949
22695421,Lizard Buzzard,Least Concern,0.63,0.31
22696134,Long-crested Eagle,Least Concern,1.12,1.445
22694976,Long-tailed Honey Buzzard,Least Concern,1.1,0.65
22693392,Long-toed Stint,Least Concern,0.26,0.032
22697859,Macronectes halli,Least Concern,1.5,3.58
22695121,Madagascar Fish Eagle,Critically Endangered,1.65,3.15
22696368,Malagasy Kestrel,Least Concern,0.49,0.145
22680339,Marbled Teal,Near Threatened,0.63,0.492
22693216,Marsh Sandpiper,Least Concern,0.55,0.076
22696373,Mauritius Kestrel,Endangered,0.49,0.196
22696453,Merlin,Least Concern,0.5,0.212
22695066,Mississippi Kite,Least Concern,0.75,0.311
22679839,Mute Swan,Least Concern,2.1,9.67
22696391,Nankeen Kestrel,Least Concern,0.66,0.182
22696476,New Zealand Falcon,Least Concern,0.7,0.531
62290750,New Zealand Plover,Critically Endangered,0.46,0.11
62023787,Ninox boobook,Least Concern,0.6,0.316
22696516,Orange-breasted Falcon,Near Threatened,0.69,0.525
22696197,Ornate Hawk-Eagle,Near Threatened,1.05,1.469
22694961,Pacific Baza,Least Concern,0.8,0.347
22680217,Pacific Black Duck,Least Concern,0.82,0.981
22694279,Pacific Gull,Least Concern,1.4,1.077
22695438,Pale Chanting Goshawk,Least Concern,1,1
22695130,Pallas’s Fish Eagle,Endangered,1.8,3.207
22693408,Pectoral Sandpiper,Least Concern,0.37,0.0651
22695402,Pied Harrier,Least Concern,1.1,0.42
22696574,Pied-billed Grebe,Least Concern,0.51,0.358
22692983,Pin-tailed Sandgrouse,Least Concern,0.54,0.225
22680336,Pink-eared Duck,Least Concern,0.57,0.344
22693085,Pintail Snipe,Least Concern,0.44,0.132
22689389,Powerful Owl,Least Concern,1.1,1.253
22695699,Red Goshawk,Endangered,1.1,1.1
22679199,Red Junglefowl,Least Concern,0.69,0.5
22727705,Red-chested Goshawk,Least Concern,0.6,0.315
22695883,Red-shouldered Hawk,Least Concern,0.9,0.701
22696229,Red-throated Caracara,Least Concern,1,0.665
22680367,Redhead,Least Concern,0.74,0.99
22694317,Ring-billed Gull,Least Concern,1.1,0.471
22680370,Ring-necked Duck,Least Concern,0.61,0.671
22690066,Rock Dove,Least Concern,0.68,0.267
22678684,Rock Partridge,Near Threatened,0.46,0.565
22727750,Ruddy Duck,Least Concern,0.53,0.499
22680003,Ruddy Shelduck,Least Concern,1.2,1.1
22695808,Rufous Crab Hawk,Near Threatened,0.9,0.796
22695936,Rufous-tailed Hawk,Vulnerable,1.2,1.14
22696495,Saker Falcon,Endangered,1,1.13
22695832,Savanna Hawk,Least Concern,1.21,0.8458
22680488,Scaly-sided Merganser,Endangered,0.7,0.985
45061132,Scopoli's Shearwater,Least Concern,1.17,0.8173
22695775,Semiplumbeous Hawk,Least Concern,0.51,0.325
22693414,Sharp-tailed Sandpiper,Vulnerable,0.36,0.0635
22695048,Snail Kite,Least Concern,0.99,0.446
22697885,Snow Petrel,Least Concern,0.8,0.293
22694053,Sociable Lapwing,Critically Endangered,0.64,0.2
22693239,Solitary Sandpiper,Least Concern,0.55,0.0521
22697870,Southern Fulmar,Least Concern,1.14,0.745
22697852,Southern Giant Petrel,Least Concern,1.5,3.85
22698314,Southern Royal Albatross,Vulnerable,2.9,7.7
22696305,Spot-winged Falconet,Least Concern,0.47,0.188
22695376,Spotted Harrier,Least Concern,1.2,0.671
22695147,Steller’s Sea Eagle,Vulnerable,2,7.9
22727499,Subantarctic Snipe,Near Threatened,0.3,0.1161
22695903,Swainson's Hawk,Least Concern,1.2,1.109
22695017,Swallow-tailed Kite,Least Concern,1.19,0.612
22692893,Tribonyx ventralis,Least Concern,0.55,0.364
22679859,Trumpeter Swan,Least Concern,2.3,10.3
22696067,Verreaux's Eagle,Least Concern,1.8,4.45
22679753,Wandering Whistling Duck,Least Concern,0.8,0.732
22692789,Watercock,Least Concern,0.68,0.303
22696064,Wedge-tailed Eagle,Least Concern,1.8,3.81
22694337,Western Gull,Least Concern,1.32,0.7998
22693376,Western Sandpiper,Least Concern,0.28,0.031
22694764,Whiskered Tern,Least Concern,0.64,0.078
22695091,Whistling Kite,Least Concern,1.2,0.84
22695097,White-bellied Sea Eagle,Least Concern,1.8,3.33
22698465,White-bellied Storm Petrel,Least Concern,0.46,0.052
22679814,White-headed Duck,Endangered,0.62,0.593
22693399,White-rumped Sandpiper,Least Concern,0.36,0.0458
22693319,Willet,Least Concern,0.56,0.3014
22695926,Zone-tailed Hawk,Least Concern,1.2,0.89
22695033,black-shouldered kite,Least Concern,0.82,0.293
22692067,brolga,Least Concern,2,5.663
22694473,ivory gull,Near Threatened,1.04,0.507
22694329,kelp gull,Least Concern,1.28,0.832
22680441,surf scoter,Least Concern,0.76,0.9"""

val birds:List<Bird> = birdsCSV.lines().map { line ->
    val (id, name, status, wingspan, mass) = line.split(',')

    Bird(id.toLong(), name, ConservationStatus.byName(status), wingspan.toFloat(), mass.toFloat())
}