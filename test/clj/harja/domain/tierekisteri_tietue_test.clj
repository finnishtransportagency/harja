(ns harja.domain.tierekisteri-tietue-test
  (:require [clojure.test :refer [deftest is]]
            [harja.testi :refer :all]
            [harja.domain.tierekisteri-tietue :as tierekisteri-tietue]))

(def testi-tietolajin-kuvaus
  {:tunniste "tl506",
   :ominaisuudet
   [{:kenttatunniste "tunniste",
     :selite "Tunniste",
     :jarjestysnumero 1,
     :koodisto nil,
     :desimaalit nil,
     :voimassaolo
     {:alkupvm #inst "2008-09-01T21:00:00.000-00:00", :loppupvm nil},
     :alaraja nil,
     :pakollinen true,
     :tietotyyppi :merkkijono,
     :pituus 20,
     :ylaraja nil}
    {:kenttatunniste "kuntoluokitus",
     :selite "Yleinen kuntokuokitus",
     :jarjestysnumero 2,
     :koodisto
     [{:koodiryhma "kuntoluokk",
       :koodi 1,
       :lyhenne "huono",
       :selite "Ala-arvoinen",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "kuntoluokk",
       :koodi 2,
       :lyhenne "välttävä",
       :selite "Merkittäviä puutteita",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "kuntoluokk",
       :koodi 3,
       :lyhenne "tyydyttävä",
       :selite "Epäoleellisia puutteita",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "kuntoluokk",
       :koodi 4,
       :lyhenne "hyvä",
       :selite "hyvä",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "kuntoluokk",
       :koodi 5,
       :lyhenne "erinomaine",
       :selite "erinomainen",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}],
     :desimaalit nil,
     :voimassaolo
     {:alkupvm #inst "2009-03-22T22:00:00.000-00:00", :loppupvm nil},
     :alaraja nil,
     :pakollinen false,
     :tietotyyppi :koodisto,
     :pituus 1,
     :ylaraja nil}
    {:kenttatunniste "urakka",
     :selite "Urakka",
     :jarjestysnumero 3,
     :koodisto
     [{:koodiryhma "urakka",
       :koodi 100,
       :lyhenne "yrittäjä",
       :selite "Paikallinen yrittäjä hoitaa",
       :muutospvm #inst "2010-06-02T21:00:00.000-00:00"}
      {:koodiryhma "urakka",
       :koodi 400,
       :lyhenne "Kunta",
       :selite "Paikallinen kunta hoitaa",
       :muutospvm #inst "2016-02-01T22:00:00.000-00:00"}
      {:koodiryhma "urakka",
       :koodi 2062,
       :lyhenne "Ke-Suomi",
       :selite "Keski-Suomen valaistusurakka",
       :muutospvm #inst "2011-03-14T22:00:00.000-00:00"}
      {:koodiryhma "urakka",
       :koodi 3001,
       :lyhenne "betonitie",
       :selite "Betonitie",
       :muutospvm #inst "2009-06-03T21:00:00.000-00:00"}
      {:koodiryhma "urakka",
       :koodi 3002,
       :lyhenne "HTU PPS",
       :selite "HTU-piirien pääteiden päällysteiden palvelusopimus",
       :muutospvm #inst "2009-06-03T21:00:00.000-00:00"}
      {:koodiryhma "urakka",
       :koodi 3003,
       :lyhenne "Opäälpa1",
       :selite "Oulun päälllystepalvelu 1",
       :muutospvm #inst "2009-06-03T21:00:00.000-00:00"}
      {:koodiryhma "urakka",
       :koodi 3004,
       :lyhenne "Opäälpa2",
       :selite "Oulun päälllystepalvelu 2",
       :muutospvm #inst "2009-06-03T21:00:00.000-00:00"}
      {:koodiryhma "urakka",
       :koodi 3005,
       :lyhenne "KaS PS",
       :selite "Kaakkois-Suomen päällystepalvelusopimus",
       :muutospvm #inst "2009-06-03T21:00:00.000-00:00"}
      {:koodiryhma "urakka",
       :koodi 3006,
       :lyhenne "P_Kimppa",
       :selite "Pirkanmaan Kimppa 2013-17",
       :muutospvm #inst "2013-06-10T21:00:00.000-00:00"}
      {:koodiryhma "urakka",
       :koodi 4001,
       :lyhenne "U-tm",
       :selite "Uudenmaan tiemerkinnät",
       :muutospvm #inst "2009-06-03T21:00:00.000-00:00"}
      {:koodiryhma "urakka",
       :koodi 4002,
       :lyhenne "T-tm",
       :selite "Turun tiemerkinnät",
       :muutospvm #inst "2009-06-03T21:00:00.000-00:00"}
      {:koodiryhma "urakka",
       :koodi 4003,
       :lyhenne "KaS-tm",
       :selite "Kaakkois-Suomen tiemerkinnät",
       :muutospvm #inst "2009-06-03T21:00:00.000-00:00"}
      {:koodiryhma "urakka",
       :koodi 4004,
       :lyhenne "H-tm",
       :selite "Hämeen tiemerkinnät",
       :muutospvm #inst "2009-06-03T21:00:00.000-00:00"}
      {:koodiryhma "urakka",
       :koodi 4005,
       :lyhenne "SK-tm",
       :selite "Savo-Karjalan tiemerkinnät",
       :muutospvm #inst "2009-06-03T21:00:00.000-00:00"}
      {:koodiryhma "urakka",
       :koodi 4006,
       :lyhenne "KeS-tm",
       :selite "Keski-Suomen tiemerkinnät",
       :muutospvm #inst "2009-06-03T21:00:00.000-00:00"}
      {:koodiryhma "urakka",
       :koodi 4007,
       :lyhenne "V-tm",
       :selite "Vaasan tiemerkinnät",
       :muutospvm #inst "2009-06-03T21:00:00.000-00:00"}
      {:koodiryhma "urakka",
       :koodi 4008,
       :lyhenne "O-tm",
       :selite "Oulun tiemerkinnät",
       :muutospvm #inst "2009-06-03T21:00:00.000-00:00"}
      {:koodiryhma "urakka",
       :koodi 4009,
       :lyhenne "L-tm",
       :selite "Lapin tiemerkinnät",
       :muutospvm #inst "2009-06-03T21:00:00.000-00:00"}
      {:koodiryhma "urakka",
       :koodi 5001,
       :lyhenne "OSiltopa1",
       :selite "Oulun siltojen palvelusopimus 1",
       :muutospvm #inst "2016-02-03T22:00:00.000-00:00"}
      {:koodiryhma "urakka",
       :koodi 5002,
       :lyhenne "OSiltopa2",
       :selite "Oulun siltojen palvelusopimus 2",
       :muutospvm #inst "2016-02-03T22:00:00.000-00:00"}
      {:koodiryhma "urakka",
       :koodi 5003,
       :lyhenne "KaSSiltopa",
       :selite "Kaakkois-Suomen siltojen palvelusopimus",
       :muutospvm #inst "2009-06-03T21:00:00.000-00:00"}
      {:koodiryhma "urakka",
       :koodi 6001,
       :lyhenne "U-telemat",
       :selite "Uudenmaan tiepiirin telematiikan hoito 2009-2012",
       :muutospvm #inst "2009-09-13T21:00:00.000-00:00"}
      {:koodiryhma "urakka",
       :koodi 6002,
       :lyhenne "Nevia",
       :selite "Valtakunnallinen matka-ajan seurantasopimus",
       :muutospvm #inst "2010-01-25T22:00:00.000-00:00"}
      {:koodiryhma "urakka",
       :koodi 9001,
       :lyhenne "JCDeaux",
       :selite "JCDeauxin mainossoppari",
       :muutospvm #inst "2013-10-23T21:00:00.000-00:00"}],
     :desimaalit nil,
     :voimassaolo
     {:alkupvm #inst "2016-02-03T22:00:00.000-00:00", :loppupvm nil},
     :alaraja nil,
     :pakollinen false,
     :tietotyyppi :koodisto,
     :pituus 6,
     :ylaraja nil}
    {:kenttatunniste "x",
     :selite "X-koordinaatti",
     :jarjestysnumero 4,
     :koodisto nil,
     :desimaalit nil,
     :voimassaolo
     {:alkupvm #inst "2008-10-15T21:00:00.000-00:00", :loppupvm nil},
     :alaraja nil,
     :pakollinen false,
     :tietotyyppi :numeerinen,
     :pituus 7,
     :ylaraja nil}
    {:kenttatunniste "y",
     :selite "Y-koordinaatti",
     :jarjestysnumero 5,
     :koodisto nil,
     :desimaalit nil,
     :voimassaolo
     {:alkupvm #inst "2008-10-15T21:00:00.000-00:00", :loppupvm nil},
     :alaraja nil,
     :pakollinen false,
     :tietotyyppi :numeerinen,
     :pituus 7,
     :ylaraja nil}
    {:kenttatunniste "z",
     :selite "Z-koordinaatti",
     :jarjestysnumero 6,
     :koodisto nil,
     :desimaalit nil,
     :voimassaolo
     {:alkupvm #inst "2008-10-15T21:00:00.000-00:00", :loppupvm nil},
     :alaraja nil,
     :pakollinen false,
     :tietotyyppi :numeerinen,
     :pituus 7,
     :ylaraja nil}
    {:kenttatunniste "lmnumero",
     :selite "liikennemerkin asetusnumero",
     :jarjestysnumero 7,
     :koodisto
     [{:koodiryhma "lmnumero",
       :koodi 10,
       :lyhenne "10",
       :selite "Kunnanraja",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 11,
       :lyhenne "11",
       :selite "Maakunnanraja",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 111,
       :lyhenne "111",
       :selite "Mutka oikealle",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 112,
       :lyhenne "112",
       :selite "Mutka vasemmalle",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 113,
       :lyhenne "113",
       :selite "Mutkia, joista ensimmäinen oikealle",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 114,
       :lyhenne "114",
       :selite "Mutkia, joista ensimmäinen vasemmalle",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 115,
       :lyhenne "115",
       :selite "Jyrkkä alamäki",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 116,
       :lyhenne "116",
       :selite "Jyrkkä ylämäki",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 121,
       :lyhenne "121",
       :selite "Kapeneva tie",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 122,
       :lyhenne "122",
       :selite "Kaksisuuntainen liikenne",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 131,
       :lyhenne "131",
       :selite "Avattava silta",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 132,
       :lyhenne "132",
       :selite "Lautta, laituri tai ranta",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 133,
       :lyhenne "133",
       :selite "Liikenneruuhka",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 141,
       :lyhenne "141",
       :selite "Epätasainen tie",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 142,
       :lyhenne "142",
       :selite "Tietyö",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 143,
       :lyhenne "143",
       :selite "Irtokiviä",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 144,
       :lyhenne "144",
       :selite "Liukas ajorata",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 147,
       :lyhenne "147",
       :selite "Vaarallinen tien reuna",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 151,
       :lyhenne "151",
       :selite "Suojatien ennakkovaroitus",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 152,
       :lyhenne "152",
       :selite "Lapsia",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 153,
       :lyhenne "153",
       :selite "Pyöräilijöitä",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 154,
       :lyhenne "154",
       :selite "Hiihtolatu",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 155,
       :lyhenne "155",
       :selite "Hirvieläimiä",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 156,
       :lyhenne "156",
       :selite "Poroja",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 161,
       :lyhenne "161",
       :selite "Tienristeys",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 162,
       :lyhenne "162",
       :selite "Sivutien risteys",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 163,
       :lyhenne "163",
       :selite "Sivutien risteys",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 164,
       :lyhenne "164",
       :selite "Sivutien risteys",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 165,
       :lyhenne "165",
       :selite "Liikennevalot",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 166,
       :lyhenne "166",
       :selite "Liikenneympyrä",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 167,
       :lyhenne "167",
       :selite "Raitiotie",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 171,
       :lyhenne "171",
       :selite "Rautatien tasoristeys ilman puomeja",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 172,
       :lyhenne "172",
       :selite "Rautatien tasoristeys, jossa on puomit",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 173,
       :lyhenne "173",
       :selite "Rautatien tasoristeyksen lähestymismerkki",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 174,
       :lyhenne "174",
       :selite "Rautatien tasoristeyksen lähestymismerkki ",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 175,
       :lyhenne "175",
       :selite "Rautatien tasoristeyksen lähestymismerkki",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 176,
       :lyhenne "176",
       :selite "Yksiraiteisen rautatien tasoristeys",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 177,
       :lyhenne "177",
       :selite "Kaksi tai useampiraiteisen rautatien tasoristeys",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 181,
       :lyhenne "181",
       :selite "Putoavia kiviä",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 182,
       :lyhenne "182",
       :selite "Matalalla lentäviä lentokoneita",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 183,
       :lyhenne "183",
       :selite "Sivutuuli",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 189,
       :lyhenne "189",
       :selite "Muu vaara",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 211,
       :lyhenne "211",
       :selite "Etuajooikeutettu tie",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 212,
       :lyhenne "212",
       :selite "Etuajo-oikeuden päättyminen",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 221,
       :lyhenne "221",
       :selite "Etuajo-oikeus kohdattaessa",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 222,
       :lyhenne "222",
       :selite "Väistämisvelvollisuus kohdattaessa",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 231,
       :lyhenne "231",
       :selite "Väistämisvelvollisuus risteyksessä",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 232,
       :lyhenne "232",
       :selite "Pakollinen pysäyttäminen",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 311,
       :lyhenne "311",
       :selite "Ajoneuvolla ajo kielletty",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 312,
       :lyhenne "312",
       :selite "Moottorikäyttöisellä ajoveuvolla ajo kielletty",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 313,
       :lyhenne "313",
       :selite "Kuorma- ja pakettiautolla ajo kielletty",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 314,
       :lyhenne "314",
       :selite "Ajoneuvoyhdistelmällä ajo kielletty",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 315,
       :lyhenne "315",
       :selite "Traktorilla ajo kielletty",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 316,
       :lyhenne "316",
       :selite "Moottoripyörällä ajo kielletty",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 317,
       :lyhenne "317",
       :selite "Moottorikelkalla ajo kielletty",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 318,
       :lyhenne "318",
       :selite "Vaarallisten aineiden kuljetus kielletty",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 319,
       :lyhenne "319",
       :selite "Linja-autolla ajo kielletty",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 321,
       :lyhenne "321",
       :selite "Mopolla ajo kielletty",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 322,
       :lyhenne "322",
       :selite "Polkupyörällä ja mopolla ajo kielletty",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 323,
       :lyhenne "323",
       :selite "Jalankulku kielletty",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 324,
       :lyhenne "324",
       :selite "Jalankulku sekä polkupyörällä ja mopolla ajo kiell",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 325,
       :lyhenne "325",
       :selite "Ratsastus kielletty",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 331,
       :lyhenne "331",
       :selite "Kielletty ajosuunta",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 332,
       :lyhenne "332",
       :selite "Vasemmalle kääntyminen kielletty",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 333,
       :lyhenne "333",
       :selite "Oikealle kääntyminen kielletty",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 334,
       :lyhenne "334",
       :selite "U-käännös kielletty",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 341,
       :lyhenne "341",
       :selite "Ajoneuvon suurin sallittu leveys",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 342,
       :lyhenne "342",
       :selite "Ajoneuvon suurin sallittu korkeus",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 343,
       :lyhenne "343",
       :selite "Ajoneuvon tai ajoneuvoyhdistelmän suurin sallittu",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 344,
       :lyhenne "344",
       :selite "Ajoneuvon suurin sallittu massa",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 345,
       :lyhenne "345",
       :selite "Ajoneuvoyhdistelmän suurin sallittu massa",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 346,
       :lyhenne "346",
       :selite "Ajoneuvon suurin sallittu akselille kohdistuva mas",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 347,
       :lyhenne "347",
       :selite "Ajoneuvon suurin sallittu telille kohdistuva massa",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 351,
       :lyhenne "351",
       :selite "Ohituskielto",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 352,
       :lyhenne "352",
       :selite "Ohituskielto päättyy",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 353,
       :lyhenne "353",
       :selite "Ohituskielto kuorma-autolla",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 354,
       :lyhenne "354",
       :selite "Ohituskielto kuorma-autolla päättyy",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 361,
       :lyhenne "361",
       :selite "Nopeusrajoitus",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 362,
       :lyhenne "362",
       :selite "Nopeusrajoitus päättyy",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 363,
       :lyhenne "363",
       :selite "Nopeusrajoitusalue",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 364,
       :lyhenne "364",
       :selite "Nopeusrajoitusalue päättyy",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 365,
       :lyhenne "365",
       :selite "Ajokaistakohtainen kielto tai rajoitus",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 371,
       :lyhenne "371",
       :selite "Pysäyttäminen kielletty",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 372,
       :lyhenne "372",
       :selite "Pysäköinti kielletty",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 373,
       :lyhenne "373",
       :selite "Pysäköintikieltoalue",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 374,
       :lyhenne "374",
       :selite "Pysäköintikieltoalue päättyy",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 375,
       :lyhenne "375",
       :selite "Taksiasemaalue",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 376,
       :lyhenne "376",
       :selite "Taksin pysäyttämispaikka",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 381,
       :lyhenne "381",
       :selite "Vuoropysäköinti",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 382,
       :lyhenne "382",
       :selite "Vuoropysäköinti",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 391,
       :lyhenne "391",
       :selite "Pakollinen pysäyttäminen tullitarkastusta varten",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 392,
       :lyhenne "392",
       :selite "Pakollinen pysäyttäminen tarkastusta varten",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 393,
       :lyhenne "393",
       :selite "Moottorikäyttöisten ajoneuvojen vähimmäisetäisyys",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 411,
       :lyhenne "411",
       :selite "Pakollinen ajosuunta",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 412,
       :lyhenne "412",
       :selite "Pakollinen ajosuunta",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 413,
       :lyhenne "413",
       :selite "Pakollinen ajosuunta",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 414,
       :lyhenne "414",
       :selite "Pakollinen ajosuunta",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 415,
       :lyhenne "415",
       :selite "Pakollinen ajosuunta",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 416,
       :lyhenne "416",
       :selite "Pakollinen kiertosuunta",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 417,
       :lyhenne "417",
       :selite "Liikenteenjakaja",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 418,
       :lyhenne "418",
       :selite "Liikenteenjakaja",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 421,
       :lyhenne "421",
       :selite "Jalkakäytävä",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 422,
       :lyhenne "422",
       :selite "Pyörätie",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 423,
       :lyhenne "423",
       :selite "Yhdistetty pyörätie ja jalkakäytävä",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 424,
       :lyhenne "424",
       :selite "Pyörätie ja jalkakäytävä rinnakkain",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 425,
       :lyhenne "425",
       :selite "Pyörätie ja jalkakäytävä rinnakkain",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 426,
       :lyhenne "426",
       :selite "Moottorikelkkailureitti",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 427,
       :lyhenne "427",
       :selite "Ratsastustie",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 511,
       :lyhenne "511",
       :selite "Suojatie",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 520,
       :lyhenne "520",
       :selite "Liityntäpysäköintipaikka",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 521,
       :lyhenne "521",
       :selite "Pysäköintipaikka",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 522,
       :lyhenne "522",
       :selite "Kohtaamispaikka",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 531,
       :lyhenne "531",
       :selite "Paikallisliikenteen linja-auton pysäkki",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 532,
       :lyhenne "532",
       :selite "Kaukoliikenteen linja-auton pysäkki",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 533,
       :lyhenne "533",
       :selite "Raitiovaunun pysäkki",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 534,
       :lyhenne "534",
       :selite "Taksiasema",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 551,
       :lyhenne "551",
       :selite "Yksisuuntainen tie",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 561,
       :lyhenne "561",
       :selite "Moottoritie",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 562,
       :lyhenne "562",
       :selite "Moottoritie päättyy",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 563,
       :lyhenne "563",
       :selite "Moottoriliikennetie",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 564,
       :lyhenne "564",
       :selite "Moottoriliikennetie päättyy",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 565,
       :lyhenne "565",
       :selite "Tunneli",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 566,
       :lyhenne "566",
       :selite "Tunneli päättyy",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 567,
       :lyhenne "567",
       :selite "Hätäpysäyttämispaikka",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 571,
       :lyhenne "571",
       :selite "Taajama",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 572,
       :lyhenne "572",
       :selite "Taajama päättyy",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 573,
       :lyhenne "573",
       :selite "Pihakatu",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 574,
       :lyhenne "574",
       :selite "Pihakatu päättyy",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 575,
       :lyhenne "575",
       :selite "Kävelykatu",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 576,
       :lyhenne "576",
       :selite "Kävelykatu päättyy",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 611,
       :lyhenne "611",
       :selite "Suunnistustaulu",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 612,
       :lyhenne "612",
       :selite "Suunnistustaulu",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 613,
       :lyhenne "613",
       :selite "Kiertotien suunnistustaulu",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 614,
       :lyhenne "614",
       :selite "Kiertotien suunnistustaulu",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 615,
       :lyhenne "615",
       :selite "Kiertotieopastus",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 616,
       :lyhenne "616",
       :selite "Ajoreittiopastus",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 621,
       :lyhenne "621",
       :selite "Ajokaistaopastus",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 622,
       :lyhenne "622",
       :selite "Ajokaistaopastus",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 623,
       :lyhenne "623",
       :selite "Ajokaistan päättyminen",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 631,
       :lyhenne "631",
       :selite "Ajokaistan yläpuolinen viitta",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 632,
       :lyhenne "632",
       :selite "Ajokaistan yläpuolinen viitta",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 633,
       :lyhenne "633",
       :selite "Ajokaistan yläpuolinen erkanemisviitta",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 641,
       :lyhenne "641",
       :selite "Tienviitta",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 642,
       :lyhenne "642",
       :selite "Erkanemisviitta",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 643,
       :lyhenne "643",
       :selite "Yksityisen tien viitta",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 644,
       :lyhenne "644",
       :selite "Osoiteviitta",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 645,
       :lyhenne "645",
       :selite "Kevyen liikenteen viitta",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 646,
       :lyhenne "646",
       :selite "Kiertotien viitta",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 647,
       :lyhenne "647",
       :selite "Kiertotien viitta",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 648,
       :lyhenne "648",
       :selite "Paikalliskohteen viitta",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 649,
       :lyhenne "649",
       :selite "Moottori- ja moottoriliikennetien viitta",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 650,
       :lyhenne "650",
       :selite "Liityntäpysäköintiviitta",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 651,
       :lyhenne "651",
       :selite "Umpitie",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 652,
       :lyhenne "652",
       :selite "Umpitie",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 653,
       :lyhenne "653",
       :selite "Enimmäisnopeussuositus",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 661,
       :lyhenne "661",
       :selite "Etäisyystaulu",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 662,
       :lyhenne "662",
       :selite "Paikannimi",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 663,
       :lyhenne "663",
       :selite "Kansainvälisen pääliikenneväylän numero",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 664,
       :lyhenne "664",
       :selite "Valtatien numero",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 665,
       :lyhenne "665",
       :selite "Kantatien numero",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 666,
       :lyhenne "666",
       :selite "Muun maantien numero",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 667,
       :lyhenne "667",
       :selite "Opastus numeron tarkoittamalle tielle",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 671,
       :lyhenne "671",
       :selite "Moottoritien tunnus",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 672,
       :lyhenne "672",
       :selite "Moottoriliikennetien tunnus",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 673,
       :lyhenne "673",
       :selite "Lentoasema",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 674,
       :lyhenne "674",
       :selite "Autolautta",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 675,
       :lyhenne "675",
       :selite "Tavarasatama",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 676,
       :lyhenne "676",
       :selite "Teollisuusalue",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 677,
       :lyhenne "677",
       :selite "Pysäköinti",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 678,
       :lyhenne "678",
       :selite "Rautatieasema",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 679,
       :lyhenne "679",
       :selite "Linja-autoasema",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 681,
       :lyhenne "681",
       :selite "Tietyille ajoneuvoille tai ajoneuvoyhdistelmille t",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 682,
       :lyhenne "682",
       :selite "Jalankulkijoille tarkoitettu reitti",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 683,
       :lyhenne "683",
       :selite "Vammaisille tarkoitettu reitti",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 684,
       :lyhenne "684",
       :selite "Vaarallisten aineiden kuljetuksille tarkoitettu re",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 685,
       :lyhenne "685",
       :selite "Reitti, jolla on portaat",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 686,
       :lyhenne "686",
       :selite "Reitti ilman portaita",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 690,
       :lyhenne "690",
       :selite "Hätäuloskäynti",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 691,
       :lyhenne "691",
       :selite "Poistumisreitti",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 701,
       :lyhenne "701",
       :selite "Palvelukohteen opastustaulu",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 702,
       :lyhenne "702",
       :selite "Palvelukohteen opastustaulu",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 703,
       :lyhenne "703",
       :selite "Palvelukohteen erkanemisviitta",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 704,
       :lyhenne "704",
       :selite "Palvelukohteen osoiteviitta",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 710,
       :lyhenne "710",
       :selite "Radioaseman taajuus",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 711,
       :lyhenne "711",
       :selite "Opastuspiste",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 712,
       :lyhenne "712",
       :selite "Opastustoimisto",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 715,
       :lyhenne "715",
       :selite "Ensiapu",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 716,
       :lyhenne "716",
       :selite "716",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 721,
       :lyhenne "721",
       :selite "Autokorjaamo",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 722,
       :lyhenne "722",
       :selite "Huoltoasema",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 723,
       :lyhenne "723",
       :selite "Hotelli tai motelli",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 724,
       :lyhenne "724",
       :selite "Ruokailupaikka",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 725,
       :lyhenne "725",
       :selite "Kahvila tai pikaruokapaikka",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 726,
       :lyhenne "726",
       :selite "Käymälä",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 731,
       :lyhenne "731",
       :selite "Retkeilymaja",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 733,
       :lyhenne "733",
       :selite "Leirintäalue",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 734,
       :lyhenne "734",
       :selite "Matkailuajoneuvoalue",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 741,
       :lyhenne "741",
       :selite "Levähdysalue",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 742,
       :lyhenne "742",
       :selite "Ulkoilualue",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 791,
       :lyhenne "791",
       :selite "Hätäpuhelin",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 792,
       :lyhenne "792",
       :selite "Sammutin",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 811,
       :lyhenne "811",
       :selite "Kohde risteävällä tiellä",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 812,
       :lyhenne "812",
       :selite "Kohde nuolen suunnassa",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 813,
       :lyhenne "813",
       :selite "Kohde nuolen suunnassa",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 814,
       :lyhenne "814",
       :selite "Vaikutusalueen pituus",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 815,
       :lyhenne "815",
       :selite "Etäisyys kohteeseen",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 816,
       :lyhenne "816",
       :selite "Etäisyys pakolliseen pysäyttämiseen",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 821,
       :lyhenne "821",
       :selite "Vapaa leveys",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 822,
       :lyhenne "822",
       :selite "Vapaa korkeus",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 823,
       :lyhenne "823",
       :selite "Sähköjohdon korkeus",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 824,
       :lyhenne "824",
       :selite "Vaikutusalue molempiin suuntiin",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 825,
       :lyhenne "825",
       :selite "Vaikutusalue molempiin suuntiin",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 826,
       :lyhenne "826",
       :selite "Vaikutusalue nuolen suuntaan",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 827,
       :lyhenne "827",
       :selite "Vaikutusalue alkaa",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 828,
       :lyhenne "828",
       :selite "Vaikutusalue päättyy",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 831,
       :lyhenne "831",
       :selite "Henkilöauto",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 832,
       :lyhenne "832",
       :selite "Linja-auto",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 833,
       :lyhenne "833",
       :selite "Kuorma-auto",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 834,
       :lyhenne "834",
       :selite "Pakettiauto",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 835,
       :lyhenne "835",
       :selite "Matkailuajoneuvo",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 836,
       :lyhenne "836",
       :selite "Invalidin ajoneuvo",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 841,
       :lyhenne "841",
       :selite "Moottoripyörä",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 842,
       :lyhenne "842",
       :selite "Mopo",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 843,
       :lyhenne "843",
       :selite "Polkupyörä",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 844,
       :lyhenne "844",
       :selite "Pysäköintitapa",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 845,
       :lyhenne "845",
       :selite "Pysäköintitapa",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 848,
       :lyhenne "848",
       :selite "Kielto ryhmän A vaarallisten aineiden kuljetuksell",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 849,
       :lyhenne "849",
       :selite "Läpiajokielto ryhmän B vaarallisten aineiden kulje",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 851,
       :lyhenne "851",
       :selite "Voimassaoloaika arkisin ma-pe",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 852,
       :lyhenne "852",
       :selite "Voimassaoloaika lauantaisin",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 853,
       :lyhenne "853",
       :selite "Voimassaoloaika sunn. ja pyhinä",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 854,
       :lyhenne "854",
       :selite "Aikarajoitus",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 861,
       :lyhenne "861",
       :selite "Etuajo-oikeutetun liikenteen suunta",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 862,
       :lyhenne "862",
       :selite "Tukkitie",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 863,
       :lyhenne "863",
       :selite "Kaksisuuntainen pyörätie",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 871,
       :lyhenne "871",
       :selite "Tekstillinen lisäkilpi",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 872,
       :lyhenne "872",
       :selite "Tekstillinen lisäkilpi",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 880,
       :lyhenne "880",
       :selite "Hätäpuhelin ja sammutin",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 911,
       :lyhenne "911",
       :selite "Erkanemismerkki",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 916,
       :lyhenne "916",
       :selite "Kaarteen suuntamerkki",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 921,
       :lyhenne "921",
       :selite "Kiertotien suuntanuoli",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 931,
       :lyhenne "931",
       :selite "Reunamerkki",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 932,
       :lyhenne "932",
       :selite "Liikennemerkkipylvään tehostamismerkki",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 935,
       :lyhenne "935",
       :selite "Korkeusmerkki",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 941,
       :lyhenne "941",
       :selite "Alikulun korkeusmitta",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 1411,
       :lyhenne "141a",
       :selite "Töyssy",
       :muutospvm #inst "2014-09-22T21:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 1612,
       :lyhenne "1612",
       :selite "1612",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 5211,
       :lyhenne "521 a",
       :selite "Ajoneuvojen sijoitus pysäköintipaikalla",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 5212,
       :lyhenne "521 b",
       :selite "Ajoneuvojen sijoitus pysäköintipaikalla",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 5213,
       :lyhenne "521 c",
       :selite "Ajoneuvojen sijoitus pysäköintipaikalla",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 5411,
       :lyhenne "541 a",
       :selite "Linja-autokaista",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 5412,
       :lyhenne "541 b",
       :selite "Linja-autokaista",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 5421,
       :lyhenne "542 a",
       :selite "Linja-autokaista päättyy",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 5422,
       :lyhenne "542 b",
       :selite "Linja-autokaista päättyy",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 5431,
       :lyhenne "543 a",
       :selite "Raitiovaunukaista",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 5432,
       :lyhenne "543 b",
       :selite "Raitiovaunukaista",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 5441,
       :lyhenne "544 a",
       :selite "Raitiovaunukaista päättyy",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 5442,
       :lyhenne "544 b",
       :selite "Raitiovaunukaista päättyy",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 6152,
       :lyhenne "6152",
       :selite "Kiertosuositustaulu",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 6225,
       :lyhenne "6225",
       :selite "Ajokaistaopastus keskikaiteellisella tiellä",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 6441,
       :lyhenne "644 a",
       :selite "Osoiteviitan ennakkomerkki",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 6651,
       :lyhenne "665 a",
       :selite "Seututien numero Eritasoliittymän numero",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 6679,
       :lyhenne "7M51",
       :selite "Eritasoliittymän numero",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 6771,
       :lyhenne "677a",
       :selite "Katettu pysäköinti",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 6811,
       :lyhenne "6811",
       :selite "Kuorma-autolle tarkoitettu reitti",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 6812,
       :lyhenne "6812",
       :selite "Henkilöautolle tarkoitettu reitti",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 6813,
       :lyhenne "6813",
       :selite "Linja-autolle tarkoitettu reitti",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 6814,
       :lyhenne "6814",
       :selite "Pakettiautolle tarkoitettu reitti",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 6815,
       :lyhenne "6815",
       :selite "Moottoripyörälle tarkoitettu reitti",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 6816,
       :lyhenne "6816",
       :selite "Mopolle tarkoitettu reitti",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 6817,
       :lyhenne "6817",
       :selite "Traktorille tarkoitettu reitti",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 6818,
       :lyhenne "6818",
       :selite "Matkailuajoneuvolle tarkoitettu reitti",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 6819,
       :lyhenne "6819",
       :selite "Polkupyörälle tarkoitettu reitti",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 7041,
       :lyhenne "704 a",
       :selite "Palvelukohteen osoiteviitan ennakkomerkki",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 7711,
       :lyhenne "771 a",
       :selite "Matkailutie",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 7712,
       :lyhenne "771 b",
       :selite "Matkailutie",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 7721,
       :lyhenne "772 a",
       :selite "Museo tai historiallinen rakennus",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 7722,
       :lyhenne "772 c",
       :selite "Luontokohde",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 7723,
       :lyhenne "772 b",
       :selite "Maailmanperintökohde",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 7724,
       :lyhenne "772 d",
       :selite "Näköalapaikka",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 7725,
       :lyhenne "772 e",
       :selite "Eläintarha tai -puisto",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 7726,
       :lyhenne "772 f",
       :selite "Muu nähtävyys",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 7731,
       :lyhenne "773 a",
       :selite "Uintipaikka",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 7732,
       :lyhenne "773 b",
       :selite "Kalastuspaikka",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 7733,
       :lyhenne "773 c",
       :selite "Hiihtohissi",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 7734,
       :lyhenne "773 d",
       :selite "Golfkenttä",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 7735,
       :lyhenne "773 e",
       :selite "Huvi- tai teemapuisto",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 7741,
       :lyhenne "774 a",
       :selite "Mökkimajoitus",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 7742,
       :lyhenne "774 b",
       :selite "Aamiaismajoitus",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 7743,
       :lyhenne "774 c",
       :selite "Suoramyyntipaikka",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 7744,
       :lyhenne "774 d",
       :selite "Käsityöpaja",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 7745,
       :lyhenne "774 e",
       :selite "Kotieläinpiha",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 7746,
       :lyhenne "774 f",
       :selite "Ratsastuspaikka",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 8551,
       :lyhenne "855 a",
       :selite "Maksullinen pysäköinti",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 8552,
       :lyhenne "855 b",
       :selite "Maksullinen pysäköinti",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 8561,
       :lyhenne "856 a",
       :selite "Pysäköintikiekon käyttövelvollisuus (kieltomerkin",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 8562,
       :lyhenne "856 b",
       :selite "Pysäköintikiekon käyttövelvollisuus (pysäköintipai",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 8611,
       :lyhenne "861 a",
       :selite "Etuajo-oikeutetun liikenteen suunta",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 8612,
       :lyhenne "861 b",
       :selite "Etuajo-oikeutetun liikenteen suunta",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 9151,
       :lyhenne "9151",
       :selite "Taustamerkki",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 9511,
       :lyhenne "9511",
       :selite "Ajovalojen käyttö",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 9512,
       :lyhenne "9512",
       :selite "Ajovalojen käyttö",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 9513,
       :lyhenne "9513",
       :selite "Ajovalojen käyttö",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 9514,
       :lyhenne "9514",
       :selite "Ajovalojen käyttö",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 9516,
       :lyhenne "9516",
       :selite "Väistämisvelvollisuus muuttunut",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 9901,
       :lyhenne "9901",
       :selite "Kameravalvonta",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 9902,
       :lyhenne "9902",
       :selite "Tiekirkko",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 9998,
       :lyhenne "muu",
       :selite "Muu merkki (mm. ei numeroa)",
       :muutospvm #inst "2012-11-14T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 9999,
       :lyhenne "9999",
       :selite "Tyhjä varsi (ei merkkiä)",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 91522,
       :lyhenne "91522",
       :selite "Taustamerkki varalaskupaikalla",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 95119,
       :lyhenne "95119",
       :selite "Yleiset nopeusrajoitukset",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 95120,
       :lyhenne "95120",
       :selite "Maantie päättyy",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 95122,
       :lyhenne "95122",
       :selite "Poronhoitoalueen merkitseminen",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 95123,
       :lyhenne "95123",
       :selite "Poronhoitoalueen merkitseminen",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 95124,
       :lyhenne "95124",
       :selite "Euroopan unionin merkki rajalla",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 95125,
       :lyhenne "95125",
       :selite "Euroopan unionin merkki rajalla",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 95126,
       :lyhenne "95126",
       :selite "Euroopan unionin merkki rajalla",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 95127,
       :lyhenne "95127",
       :selite "Euroopan unionin merkki rajalla",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmnumero",
       :koodi 95128,
       :lyhenne "95128",
       :selite "Euroopan unionin merkki rajalla",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}],
     :desimaalit nil,
     :voimassaolo
     {:alkupvm #inst "2014-09-22T21:00:00.000-00:00", :loppupvm nil},
     :alaraja nil,
     :pakollinen false,
     :tietotyyppi :koodisto,
     :pituus 4,
     :ylaraja nil}
    {:kenttatunniste "sivutie",
     :selite "maantien asemesta sivutiellä",
     :jarjestysnumero 8,
     :koodisto
     [{:koodiryhma "sivutie",
       :koodi 1,
       :lyhenne "yks.tie",
       :selite "merkki yksityistien puolella",
       :muutospvm #inst "2010-06-02T21:00:00.000-00:00"}
      {:koodiryhma "sivutie",
       :koodi 2,
       :lyhenne "katu",
       :selite "merkki kadun tai kaavatien puolella",
       :muutospvm #inst "2010-06-02T21:00:00.000-00:00"}
      {:koodiryhma "sivutie",
       :koodi 9,
       :lyhenne "ei tietoa",
       :selite "yksityistie tai katu, selvittämättä",
       :muutospvm #inst "2010-06-02T21:00:00.000-00:00"}],
     :desimaalit nil,
     :voimassaolo
     {:alkupvm #inst "2010-06-02T21:00:00.000-00:00", :loppupvm nil},
     :alaraja nil,
     :pakollinen false,
     :tietotyyppi :koodisto,
     :pituus 1,
     :ylaraja nil}
    {:kenttatunniste "lmala",
     :selite "pinta-ala",
     :jarjestysnumero 9,
     :koodisto nil,
     :desimaalit nil,
     :voimassaolo
     {:alkupvm #inst "2009-03-01T22:00:00.000-00:00", :loppupvm nil},
     :alaraja nil,
     :pakollinen false,
     :tietotyyppi :numeerinen,
     :pituus 5,
     :ylaraja nil}
    {:kenttatunniste "lmvanha",
     :selite "vanha tyyppi",
     :jarjestysnumero 10,
     :koodisto
     [{:koodiryhma "totuusarvo",
       :koodi 0,
       :lyhenne "ei",
       :selite "ei",
       :muutospvm #inst "2009-09-17T21:00:00.000-00:00"}
      {:koodiryhma "totuusarvo",
       :koodi 1,
       :lyhenne "on",
       :selite "on",
       :muutospvm #inst "2009-09-17T21:00:00.000-00:00"}],
     :desimaalit nil,
     :voimassaolo
     {:alkupvm #inst "2010-06-02T21:00:00.000-00:00", :loppupvm nil},
     :alaraja nil,
     :pakollinen false,
     :tietotyyppi :koodisto,
     :pituus 1,
     :ylaraja nil}
    {:kenttatunniste "lmtyyppi",
     :selite "liikennemerkin tyyppi",
     :jarjestysnumero 11,
     :koodisto
     [{:koodiryhma "lmtyyppi",
       :koodi 1,
       :lyhenne "tavallinen",
       :selite "tavallinen",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmtyyppi",
       :koodi 2,
       :lyhenne "muuttuva",
       :selite "muuttuva merkki",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmtyyppi",
       :koodi 3,
       :lyhenne "käännettäv",
       :selite "käännettävä merkki",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmtyyppi",
       :koodi 4,
       :lyhenne "kaksipuoli",
       :selite "kaksipuolinen",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}],
     :desimaalit nil,
     :voimassaolo
     {:alkupvm #inst "2010-06-02T21:00:00.000-00:00", :loppupvm nil},
     :alaraja nil,
     :pakollinen false,
     :tietotyyppi :koodisto,
     :pituus 1,
     :ylaraja nil}
    {:kenttatunniste "lmkausi",
     :selite "kausiluontoinen liikennemerkki",
     :jarjestysnumero 12,
     :koodisto
     [{:koodiryhma "totuusarvo",
       :koodi 0,
       :lyhenne "ei",
       :selite "ei",
       :muutospvm #inst "2009-09-17T21:00:00.000-00:00"}
      {:koodiryhma "totuusarvo",
       :koodi 1,
       :lyhenne "on",
       :selite "on",
       :muutospvm #inst "2009-09-17T21:00:00.000-00:00"}],
     :desimaalit nil,
     :voimassaolo
     {:alkupvm #inst "2010-06-02T21:00:00.000-00:00", :loppupvm nil},
     :alaraja nil,
     :pakollinen false,
     :tietotyyppi :koodisto,
     :pituus 1,
     :ylaraja nil}
    {:kenttatunniste "lmkiinnit",
     :selite "liikennemerkin kiinnitys",
     :jarjestysnumero 13,
     :koodisto
     [{:koodiryhma "lmkiinnit",
       :koodi 1,
       :lyhenne "putkivarsi",
       :selite "putkivarsi",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmkiinnit",
       :koodi 2,
       :lyhenne "kehys",
       :selite "kehys",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmkiinnit",
       :koodi 3,
       :lyhenne "muu kiinn.",
       :selite "kiinni muussa rakenteessa",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "lmkiinnit",
       :koodi 5,
       :lyhenne "porttaali",
       :selite "kokoporttaali",
       :muutospvm #inst "2012-11-22T22:00:00.000-00:00"}
      {:koodiryhma "lmkiinnit",
       :koodi 6,
       :lyhenne "puoliportt",
       :selite "puoliporttaali",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}],
     :desimaalit nil,
     :voimassaolo
     {:alkupvm #inst "2012-11-22T22:00:00.000-00:00", :loppupvm nil},
     :alaraja nil,
     :pakollinen false,
     :tietotyyppi :koodisto,
     :pituus 1,
     :ylaraja nil}
    {:kenttatunniste "lmmater",
     :selite "liikennemerkin materiaali",
     :jarjestysnumero 14,
     :koodisto
     [{:koodiryhma "materiaali",
       :koodi 1,
       :lyhenne "alumiini",
       :selite "alumiini",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "materiaali",
       :koodi 2,
       :lyhenne "vaneri",
       :selite "vaneri",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "materiaali",
       :koodi 3,
       :lyhenne "lasi",
       :selite "lasi",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "materiaali",
       :koodi 4,
       :lyhenne "puu",
       :selite "puu",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "materiaali",
       :koodi 5,
       :lyhenne "metalli",
       :selite "metalli",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "materiaali",
       :koodi 6,
       :lyhenne "valurauta",
       :selite "valurauta",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "materiaali",
       :koodi 7,
       :lyhenne "maa-aines",
       :selite "maa-aines",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "materiaali",
       :koodi 9,
       :lyhenne "muu",
       :selite "muu materiaali",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "materiaali",
       :koodi 11,
       :lyhenne "betoni",
       :selite "betoni",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "materiaali",
       :koodi 12,
       :lyhenne "muovi",
       :selite "muovi",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "materiaali",
       :koodi 13,
       :lyhenne "teräs",
       :selite "teräs",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "materiaali",
       :koodi 14,
       :lyhenne "kivi",
       :selite "kivi",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "materiaali",
       :koodi 15,
       :lyhenne "pleksi",
       :selite "pleksi",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}
      {:koodiryhma "materiaali",
       :koodi 16,
       :lyhenne "lasikuitu",
       :selite "lasikuitu",
       :muutospvm #inst "2009-03-22T22:00:00.000-00:00"}],
     :desimaalit nil,
     :voimassaolo
     {:alkupvm #inst "2010-06-02T21:00:00.000-00:00", :loppupvm nil},
     :alaraja nil,
     :pakollinen false,
     :tietotyyppi :koodisto,
     :pituus 1,
     :ylaraja nil}
    {:kenttatunniste "lmteksti",
     :selite "teksti",
     :jarjestysnumero 15,
     :koodisto nil,
     :desimaalit nil,
     :voimassaolo
     {:alkupvm #inst "2015-10-20T21:00:00.000-00:00", :loppupvm nil},
     :alaraja nil,
     :pakollinen false,
     :tietotyyppi :merkkijono,
     :pituus 99,
     :ylaraja nil}
    {:kenttatunniste "lmomist",
     :selite "omistaja",
     :jarjestysnumero 16,
     :koodisto nil,
     :desimaalit nil,
     :voimassaolo
     {:alkupvm #inst "2010-06-02T21:00:00.000-00:00", :loppupvm nil},
     :alaraja nil,
     :pakollinen false,
     :tietotyyppi :merkkijono,
     :pituus 50,
     :ylaraja nil}
    {:kenttatunniste "opastetunn",
     :selite "liikennemerkkitolpan tunnus",
     :jarjestysnumero 17,
     :koodisto nil,
     :desimaalit nil,
     :voimassaolo
     {:alkupvm #inst "2010-06-02T21:00:00.000-00:00", :loppupvm nil},
     :alaraja nil,
     :pakollinen false,
     :tietotyyppi :merkkijono,
     :pituus 20,
     :ylaraja nil}
    {:kenttatunniste "VOIMASSAOLO_LOPPU",
     :selite "tieosoitteen voimassaolon loppumispäivämäärä",
     :jarjestysnumero nil,
     :koodisto nil,
     :desimaalit nil,
     :voimassaolo
     {:alkupvm #inst "2014-11-24T22:00:00.000-00:00", :loppupvm nil},
     :alaraja nil,
     :pakollinen false,
     :tietotyyppi :paivamaara,
     :pituus 8,
     :ylaraja nil}
    {:kenttatunniste "loppupvm",
     :selite "voimassaolon loppumispäivämäärä",
     :jarjestysnumero nil,
     :koodisto nil,
     :desimaalit nil,
     :voimassaolo
     {:alkupvm #inst "2014-11-24T22:00:00.000-00:00", :loppupvm nil},
     :alaraja nil,
     :pakollinen false,
     :tietotyyppi :paivamaara,
     :pituus 8,
     :ylaraja nil}
    {:kenttatunniste "puoli",
     :selite "puolitieto",
     :jarjestysnumero nil,
     :koodisto nil,
     :desimaalit nil,
     :voimassaolo
     {:alkupvm #inst "2014-11-24T22:00:00.000-00:00", :loppupvm nil},
     :alaraja nil,
     :pakollinen false,
     :tietotyyppi :numeerinen,
     :pituus nil,
     :ylaraja nil}
    {:kenttatunniste "VOIMASSAOLO_ALKU",
     :selite "tieosoitteen voimassaolon alkamispäivämäärä",
     :jarjestysnumero nil,
     :koodisto nil,
     :desimaalit nil,
     :voimassaolo
     {:alkupvm #inst "2014-11-24T22:00:00.000-00:00", :loppupvm nil},
     :alaraja nil,
     :pakollinen false,
     :tietotyyppi :paivamaara,
     :pituus 8,
     :ylaraja nil}
    {:kenttatunniste "lakkautuspvm",
     :selite "lakkautuspäivämäärä",
     :jarjestysnumero nil,
     :koodisto nil,
     :desimaalit nil,
     :voimassaolo
     {:alkupvm #inst "2014-11-24T22:00:00.000-00:00", :loppupvm nil},
     :alaraja nil,
     :pakollinen false,
     :tietotyyppi :paivamaara,
     :pituus 8,
     :ylaraja nil}
    {:kenttatunniste "tila",
     :selite "tila",
     :jarjestysnumero nil,
     :koodisto nil,
     :desimaalit nil,
     :voimassaolo
     {:alkupvm #inst "2014-11-24T22:00:00.000-00:00", :loppupvm nil},
     :alaraja nil,
     :pakollinen true,
     :tietotyyppi :merkkijono,
     :pituus 1,
     :ylaraja nil}
    {:kenttatunniste "ELY",
     :selite "ely / piirinumero",
     :jarjestysnumero nil,
     :koodisto nil,
     :desimaalit nil,
     :voimassaolo
     {:alkupvm #inst "2014-11-24T22:00:00.000-00:00", :loppupvm nil},
     :alaraja nil,
     :pakollinen true,
     :tietotyyppi :numeerinen,
     :pituus 2,
     :ylaraja nil}
    {:kenttatunniste "AOSA",
     :selite "tien alkuosa",
     :jarjestysnumero nil,
     :koodisto nil,
     :desimaalit nil,
     :voimassaolo
     {:alkupvm #inst "2014-11-24T22:00:00.000-00:00", :loppupvm nil},
     :alaraja nil,
     :pakollinen true,
     :tietotyyppi :numeerinen,
     :pituus 5,
     :ylaraja nil}
    {:kenttatunniste "AJR",
     :selite "ajorata",
     :jarjestysnumero nil,
     :koodisto nil,
     :desimaalit nil,
     :voimassaolo
     {:alkupvm #inst "2014-11-24T22:00:00.000-00:00", :loppupvm nil},
     :alaraja nil,
     :pakollinen true,
     :tietotyyppi :numeerinen,
     :pituus 1,
     :ylaraja nil}
    {:kenttatunniste "AET",
     :selite "tien alkuosan etaisyys",
     :jarjestysnumero nil,
     :koodisto nil,
     :desimaalit nil,
     :voimassaolo
     {:alkupvm #inst "2014-11-24T22:00:00.000-00:00", :loppupvm nil},
     :alaraja nil,
     :pakollinen true,
     :tietotyyppi :numeerinen,
     :pituus 5,
     :ylaraja nil}
    {:kenttatunniste "alkupvm",
     :selite "voimassaolon alkamispäivämäärä",
     :jarjestysnumero nil,
     :koodisto nil,
     :desimaalit nil,
     :voimassaolo
     {:alkupvm #inst "2014-11-24T22:00:00.000-00:00", :loppupvm nil},
     :alaraja nil,
     :pakollinen true,
     :tietotyyppi :paivamaara,
     :pituus 8,
     :ylaraja nil}
    {:kenttatunniste "tie",
     :selite "tien numero",
     :jarjestysnumero nil,
     :koodisto nil,
     :desimaalit nil,
     :voimassaolo
     {:alkupvm #inst "2014-11-24T22:00:00.000-00:00", :loppupvm nil},
     :alaraja nil,
     :pakollinen true,
     :tietotyyppi :numeerinen,
     :pituus 5,
     :ylaraja nil}]})

(deftest tarkista-kentan-arvon-hakeminen-merkkijonosta
  (let [kenttien-kuvaukset [{:kenttatunniste "a"
                             :jarjestysnumero 1
                             :tietotyyppi :merkkijono
                             :pituus 5}
                            {:kenttatunniste "b"
                             :jarjestysnumero 2
                             :tietotyyppi :merkkijono
                             :pituus 5}
                            {:kenttatunniste "c"
                             :jarjestysnumero 3
                             :tietotyyppi :merkkijono
                             :pituus 10}
                            {:kenttatunniste "d"
                             :jarjestysnumero 4
                             :tietotyyppi :merkkijono
                             :pituus 3}]
        arvot-string "tes  ti   testi     123"]
    (is (= "testi" (#'tierekisteri-tietue/hae-arvo arvot-string kenttien-kuvaukset 3)))
    (is (= "ti" (#'tierekisteri-tietue/hae-arvo arvot-string kenttien-kuvaukset 2)))
    (is (= "123" (#'tierekisteri-tietue/hae-arvo arvot-string kenttien-kuvaukset 4)))))

(deftest tarkista-paivamaarien-kasittely
  (let [kenttien-kuvaukset [{:kenttatunniste "a"
                      :jarjestysnumero 1
                      :tietotyyppi :paivamaara
                      :pituus 10}]
        arvot-string "2009-03-23"]
    (is (= "2009-03-23" (#'tierekisteri-tietue/hae-arvo arvot-string kenttien-kuvaukset 1)))))


(deftest testaa-tietolajin-arvot-map->string
  (let [muunnos (tierekisteri-tietue/tietolajin-arvot-map->string
                  {"a" "testi"
                   "b" "1"}
                  {:tunniste "tl506",
                   :ominaisuudet
                   [{:kenttatunniste "a"
                     :jarjestysnumero 1
                     :pakollinen true
                     :tietotyyppi :merkkijono
                     :pituus 20}
                    {:kenttatunniste "b"
                     :jarjestysnumero 2
                     :tietotyyppi :merkkijono
                     :pituus 10}]})]
    (is (= "testi               1         "
           muunnos))))

(deftest testaa-tietolajin-arvot-merkkijono->map
  (let [muunnos (tierekisteri-tietue/tietolajin-arvot-merkkijono->map
                  "testi               1         "
                  {:tunniste "tl506",
                   :ominaisuudet
                   [{:kenttatunniste "a"
                     :jarjestysnumero 1
                     :pakollinen true
                     :tietotyyppi :merkkijono
                     :pituus 20}
                    {:kenttatunniste "b"
                     :jarjestysnumero 2
                     :tietotyyppi :merkkijono
                     :pituus 10}]})]
    (is (= {"a" "testi"
            "b" "1"}
           muunnos))))

(deftest tarkista-validoinnit
  (let [tietolajin-kuvaus {:tunniste "tl506",
                           :ominaisuudet
                           [{:kenttatunniste "tie"
                             :jarjestysnumero 1
                             :pakollinen true
                             :tietotyyppi :merkkijono
                             :pituus 20}
                            {:kenttatunniste "tunniste"
                             :jarjestysnumero 2
                             :tietotyyppi :merkkijono
                             :pituus 20}]}]
    (is (thrown-with-msg? Exception #"Virhe tietolajin tl506 arvojen käsittelyssä: Pakollinen arvo puuttuu kentästä: tie"
                         (tierekisteri-tietue/tietolajin-arvot-map->string
                           {"tie" nil}
                           tietolajin-kuvaus))
       "Puuttuva pakollinen arvo huomattiin")
  (is (thrown-with-msg? Exception #"Virhe tietolajin tl506 arvojen käsittelyssä: Liian pitkä arvo kentässä: tunniste maksimipituus: 20"
                        (tierekisteri-tietue/tietolajin-arvot-map->string
                          {"tie" "123"
                           "tunniste" "1234567890112345678901"}
                          tietolajin-kuvaus))
      "Liian pitkä arvo huomattiin")))
