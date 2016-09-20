(ns harja-laadunseuranta.testidata
  "Testidataa yksikkötestejä varten"
  (:require [clojure.test :as t]
            [harja.testi :refer [db]]
            [harja-laadunseuranta.tietokanta :as tietokanta]))

(tietokanta/aseta-tietokanta! db)

(def havainto
  {:id 1
   :sijainti [65 25]

   :havainnot [17 20 :epatasainen-polanne]

   ;; seuraavat voivat olla nil jos ei annettu
   :kitkamittaus 0.3
   :lampotila 12
   :tasaisuus 2
   :lumisuus 3
   :kuvaus "foo"
   :kuva 2})

(def mockattu-tierekisteri
  {; tarkastus samalla tiellä samaan suuntaan
   [464681.5999816895 7230492.000024414] {:tie 20, :aosa 10, :aet 3921}
   [465321.5999816895 7230676.000024414] {:tie 20, :aosa 10, :aet 4587}
   [465641.5999816895 7230780.000024414] {:tie 20, :aosa 10, :aet 4924}
   [466089.5999816895 7230916.000024414] {:tie 20, :aosa 10, :aet 5392}
   [466409.5999816895 7230996.000024414] {:tie 20, :aosa 11, :aet 5721}
   [467009.5999816895 7231180.000024414] {:tie 20, :aosa 11, :aet 6349}
   [467257.5999816895 7231252.000024414] {:tie 20, :aosa 11, :aet 6607}
   [467545.5999816895 7231332.000024414] {:tie 20, :aosa 11, :aet 6906}
   [467753.5999816895 7231404.000024414] {:tie 20, :aosa 11, :aet 7126}

   ; toinen tarkastus, niin ikään samalla tiellä samaan suuntaan
   [468064.79999084474 7231495.200001526] {:tie 20, :aosa 11, :aet 237}
   [468256.79999084474 7231555.200001526] {:tie 20, :aosa 11, :aet 438}
   [468568.79999084474 7231643.200001526] {:tie 20, :aosa 11, :aet 762}
   [468768.79999084474 7231707.200001526] {:tie 20, :aosa 11, :aet 972}
   [468932.79999084474 7231763.200001526] {:tie 20, :aosa 11, :aet 1146}
   [469168.79999084474 7231867.200001526] {:tie 20, :aosa 11, :aet 1404}
   [469300.79999084474 7231931.200001526] {:tie 20, :aosa 11, :aet 1551}
   [469492.79999084474 7232011.200001526] {:tie 20, :aosa 11, :aet 1759}

   ;; tarkastus, jossa ajetaan risteykseen ja tie vaihtuu
   [455421.19997024536 7227742.400009155] {:tie 20, :aosa 7, :aet 8847}
   [455493.19997024536 7227764.400009155] {:tie 20, :aosa 7, :aet 8923}
   [455505.19997024536 7227760.400009155] {:tie 20, :aosa 7, :aet 8933}
   [455519.19997024536 7227720.400009155] {:tie 8341, :aosa 4, :aet 2597}
   [455555.19997024536 7227594.400009155] {:tie 8341, :aosa 4, :aet 2466}

   ;; tarkastus, jossa käännytään ympäri
   [454881.19997024536 7227586.400009155] {:tie 20, :aosa 7, :aet 8285}
   [455041.19997024536 7227634.400009155] {:tie 20, :aosa 7, :aet 8452}
   [455165.19997024536 7227666.400009155] {:tie 20, :aosa 7, :aet 8580}
   [455041.19997024536 7227634.400009156] {:tie 20, :aosa 7, :aet 8453}
   [454881.19997024536 7227586.400009157] {:tie 20, :aosa 7, :aet 8284}})

(def tarkastukset-joissa-jatkuvat-havainnot-muuttuu
  "Tämä tarkastus on ajettu yhteen suuntaan suht. lyhyellä tieosuudella."
  [; tarkastus 1
   {:id 0 :sijainti [464681.5999816895 7230492.000024414]
    :jatkuvat-havainnot []}
   {:id 1 :sijainti [465321.5999816895 7230676.000024414]
    :jatkuvat-havainnot []}

   ;; tarkastus 2
   {:id 2 :sijainti [465641.5999816895 7230780.000024414]
    :jatkuvat-havainnot [17]
    :kitkamittaus 0.2}
   {:id 3 :sijainti [466089.5999816895 7230916.000024414]
    :jatkuvat-havainnot [17]}
   {:id 4 :sijainti [466409.5999816895 7230996.000024414]
    :jatkuvat-havainnot [17]}
   {:id 5 :sijainti [467009.5999816895 7231180.000024414]
    :jatkuvat-havainnot [17]}
   {:id 6 :sijainti [467257.5999816895 7231252.000024414]
    :jatkuvat-havainnot [17]}
   {:id 7 :sijainti [467545.5999816895 7231332.000024414]
    :jatkuvat-havainnot [17]
    :kitkamittaus 0.3}

   ;; tarkastus 3
   {:id 8 :sijainti [467753.5999816895 7231404.000024414]
    :jatkuvat-havainnot []}])

(def soratiehavainnon-mittaukset
  [{:id 2 :sijainti [465641.5999816895 7230780.000024414]
    :jatkuvat-havainnot [19]}
   {:id 3 :sijainti [466089.5999816895 7230916.000024414]
    :jatkuvat-havainnot [19]}
   {:id 4 :sijainti [466409.5999816895 7230996.000024414]
    :jatkuvat-havainnot [19]
    :kuvaus "foo"}
   {:id 5 :sijainti [467009.5999816895 7231180.000024414]
    :jatkuvat-havainnot [19]}
   {:id 6 :sijainti [467257.5999816895 7231252.000024414]
    :jatkuvat-havainnot [19]
    :kuvaus "bar"}
   {:id 7 :sijainti [467545.5999816895 7231332.000024414]
    :jatkuvat-havainnot [19]
    :tasaisuus 3
    :kiinteys 4
    :polyavyys 5}
   {:id 8 :sijainti [467545.5999816895 7231332.000024414]
    :jatkuvat-havainnot [19]
    :tasaisuus nil}])

(def tarkastukset-joissa-jatkuvat-havainnot-muuttuu-ja-kommentteja
  "Tämä tarkastus on ajettu yhteen suuntaan suht. lyhyellä tieosuudella."
  [; tarkastus 1
   {:id 0 :sijainti [464681.5999816895 7230492.000024414]
    :jatkuvat-havainnot []}
   {:id 1 :sijainti [465321.5999816895 7230676.000024414]
    :jatkuvat-havainnot []}

   ;; tarkastus 2
   {:id 2 :sijainti [465641.5999816895 7230780.000024414]
    :jatkuvat-havainnot [17]
    :kitkamittaus 0.2}
   {:id 3 :sijainti [466089.5999816895 7230916.000024414]
    :jatkuvat-havainnot [17]}
   {:id 4 :sijainti [466409.5999816895 7230996.000024414]
    :jatkuvat-havainnot [17]
    :kuvaus "foo"}
   {:id 5 :sijainti [467009.5999816895 7231180.000024414]
    :jatkuvat-havainnot [17]}
   {:id 6 :sijainti [467257.5999816895 7231252.000024414]
    :jatkuvat-havainnot [17]
    :kuvaus "bar"}
   {:id 7 :sijainti [467545.5999816895 7231332.000024414]
    :jatkuvat-havainnot [17]
    :kitkamittaus 0.3}

   ;; tarkastus 3
   {:id 8 :sijainti [467753.5999816895 7231404.000024414]
    :jatkuvat-havainnot []}])

(def tarkastus-jossa-piste-ei-osu-tielle
  [{:id 0 :sijainti [464681.5999816895 7230492.000024414]
    :jatkuvat-havainnot [17]
    :kitkamittaus 0.3}
   ;; Mittausvirhe: piste ei osu tielle. Silti osa samaa tarkastusta, joka jatkuu tämän pisteen jälkeenkin.
   {:id 1 :sijainti [465331 7234690]
    :jatkuvat-havainnot [17]}
   {:id 2 :sijainti [465321.5999816895 7230676.000024414]
    :jatkuvat-havainnot [17]
    :kitkamittaus 0.3}])


(def tarkastus-jossa-tie-vaihtuu
  [{:id 0 :sijainti [455421.19997024536 7227742.400009155]
    :jatkuvat-havainnot []}
   {:id 1 :sijainti [455493.19997024536 7227764.400009155]
    :jatkuvat-havainnot []}
   {:id 2 :sijainti [455505.19997024536 7227760.400009155]
    :jatkuvat-havainnot []}
   {:id 3 :sijainti [455519.19997024536 7227720.400009155]
    :jatkuvat-havainnot []}
   {:id 4 :sijainti [455555.19997024536 7227594.400009155]
    :jatkuvat-havainnot []}])


(def tarkastus-jossa-kaannytaan-ympari
  [{:id 0 :sijainti [454881.19997024536 7227586.400009155]
    :jatkuvat-havainnot []}
   {:id 1 :sijainti [455041.19997024536 7227634.400009155]
    :jatkuvat-havainnot []}
   {:id 2 :sijainti [455165.19997024536 7227666.400009155]
    :jatkuvat-havainnot []}
   {:id 3 :sijainti [455041.19997024536 7227634.400009155]
    :jatkuvat-havainnot []}
   {:id 4 :sijainti [454881.19997024536 7227586.400009155]
    :jatkuvat-havainnot []}])

(def tarkastus-jossa-pistemainen-havainto
  "Tämä tarkastus on ajettu yhteen suuntaan suht. lyhyellä tieosuudella."
  [;; tarkastus 1
   {:id 0 :sijainti [464681.5999816895 7230492.000024414]
    :jatkuvat-havainnot [17]
    :kitkamittaus 0.2}
   {:id 1 :sijainti [465321.5999816895 7230676.000024414]
    :jatkuvat-havainnot [17]}
   {:id 2 :sijainti [465641.5999816895 7230780.000024414]
    :jatkuvat-havainnot [17]}
   {:id 3 :sijainti [466089.5999816895 7230916.000024414]
    :jatkuvat-havainnot [17]}
   {:id 4 :sijainti [466409.5999816895 7230996.000024414]
    :jatkuvat-havainnot [17]}

   ;; tarkastus 2
   {:id 5 :sijainti [467009.5999816895 7231180.000024414]
    :jatkuvat-havainnot [17]
    :kitkamittaus 0.2
    :pistemainen-havainto 20}

   ;; tarkastus 1 jatkuu...
   {:id 6 :sijainti [467257.5999816895 7231252.000024414]
    :jatkuvat-havainnot [17]
    :kitkamittaus 0.3}])

(def monipuolinen-tarkastus
  "Tämä tarkastus on ajettu yhteen suuntaan suht. lyhyellä tieosuudella."
  [; tarkastus 1 alkaa
   {:id 0 :sijainti [464681.5999816895 7230492.000024414]
    :jatkuvat-havainnot []
    :pistemainen-havainto nil}
   {:id 1 :sijainti [465321.5999816895 7230676.000024414]
    :jatkuvat-havainnot []
    :pistemainen-havainto nil}

   ;; tarkastus 1 päättyy, sillä liukkaus alkaa. Tästä alkaa
   ;; uusi tarkastus 2, jossa liukasta ja kaksi kitkamittausta
   {:id 2 :sijainti [465641.5999816895 7230780.000024414]
    :jatkuvat-havainnot [17]
    :pistemainen-havainto nil}
   {:id 3 :sijainti [466089.5999816895 7230916.000024414]
    :jatkuvat-havainnot [17]
    :pistemainen-havainto nil
    :kitkamittaus 0.2}

   ;; tarkastus 3, jossa pistemäinen havainto
   {:id 4 :sijainti [466409.5999816895 7230996.000024414]
    :jatkuvat-havainnot [17]
    :pistemainen-havainto 20}

   ;; tarkastus 2 jatkuu, sillä jatkuvat havainnot eivät muutu
   {:id 5 :sijainti [467009.5999816895 7231180.000024414]
    :jatkuvat-havainnot [17]
    :pistemainen-havainto nil
    :kitkamittaus 0.4}
   {:id 6 :sijainti [467257.5999816895 7231252.000024414]
    :jatkuvat-havainnot [17]
    :kuva 1
    :pistemainen-havainto nil}
   {:id 7 :sijainti [467545.5999816895 7231332.000024414]
    :jatkuvat-havainnot [17]
    :pistemainen-havainto nil}

   ;; tarkastus 3 päättyy, sillä liukkaus päättyy.
   ;; Tässä uusi tarkastus 4, jossa pistemäinen havainto
   {:id 8 :sijainti [467753.5999816895 7231404.000024414]
    :jatkuvat-havainnot []
    :pistemainen-havainto 20}

   ;; tarkastus 5, alkaa uudelta tieosalta
   {:id 9 :sijainti [468064.79999084474 7231495.200001526]
    :jatkuvat-havainnot []
    :pistemainen-havainto nil}
   {:id 10 :sijainti [468256.79999084474 7231555.200001526]
    :jatkuvat-havainnot []
    :pistemainen-havainto nil}

   ;; tarkastus 6, kuva ja seliteteksti
   {:id 11 :sijainti [468568.79999084474 7231643.200001526]
    :jatkuvat-havainnot []
    :pistemainen-havainto nil
    :kuva 1
    :kuvaus "Tiessä reikä"}

   ;; tarkastus 5 jatkuu...
   {:id 12 :sijainti [468768.79999084474 7231707.200001526]
    :jatkuvat-havainnot []
    :pistemainen-havainto nil}

   ;; tarkastus 6 päättyy ja alkaa uusi tarkastus 7, jossa uusi jatkuva havainto
   {:id 13 :sijainti [468932.79999084474 7231763.200001526]
    :jatkuvat-havainnot [:epatasainen-polanne]
    :pistemainen-havainto nil}

   ;; tarkastus 7 päättyy ja alkaa uusi tarkastus 8
   {:id 14 :sijainti [469168.79999084474 7231867.200001526]
    :jatkuvat-havainnot []
    :pistemainen-havainto nil}
   {:id 15 :sijainti [469300.79999084474 7231931.200001526]
    :jatkuvat-havainnot []
    :pistemainen-havainto nil}
   {:id 16 :sijainti [469492.79999084474 7232011.200001526]
    :jatkuvat-havainnot []
    :pistemainen-havainto nil}])

(def tarkastus-jossa-alkuosa-vaihtuu
  [{:id 1 :sijainti [465641.5999816895 7230780.000024414]
    :jatkuvat-havainnot [1]
    :pistemainen-havainto nil}
   {:id 2 :sijainti [466089.5999816895 7230916.000024414]
    :jatkuvat-havainnot [1]
    :pistemainen-havainto nil}
   {:id 3 :sijainti [466409.5999816895 7230996.000024414]
    :jatkuvat-havainnot [1]
    :pistemainen-havainto nil}
   {:id 4 :sijainti [467009.5999816895 7231180.000024414]
    :jatkuvat-havainnot [1]
    :pistemainen-havainto nil}])

(def failaava-havaintosarja
  [{:id 263 :sijainti [427864.427357845 7211581.80922425] :jatkuvat-havainnot [] :tr-osoite {:tie  8156 :aosa    1 :aet 1009}}
   {:id 264 :sijainti [427864.607903649 7211582.88154428] :jatkuvat-havainnot [] :tr-osoite {:tie  8156 :aosa    1 :aet 1010}}
   {:id 265 :sijainti [427864.806536085 7211584.06128668] :jatkuvat-havainnot [] :tr-osoite {:tie  8156 :aosa    1 :aet 1011}}
   {:id 266 :sijainti [427864.871662854 7211584.44809566] :jatkuvat-havainnot [] :tr-osoite {:tie  8156 :aosa    1 :aet 1011}}
   {:id 267 :sijainti [427864.918255227 7211584.72482285] :jatkuvat-havainnot [] :tr-osoite {:tie  8156 :aosa    1 :aet 1012}}
   {:id 268 :sijainti [427863.706764506 7211586.56782844] :jatkuvat-havainnot [] :tr-osoite {:tie  8156 :aosa    1 :aet 1013}}
   {:id 269 :sijainti [427863.119897222 7211587.46061256] :jatkuvat-havainnot [] :tr-osoite {:tie  8156 :aosa    1 :aet 1014}}
   {:id 270 :sijainti [427862.896246648 7211587.80084567] :jatkuvat-havainnot [] :tr-osoite {:tie  8156 :aosa    1 :aet 1015}}
   {:id 271 :sijainti [427862.640537326 7211588.18984882] :jatkuvat-havainnot [] :tr-osoite {:tie  8156 :aosa    1 :aet 1015}}
   {:id 272 :sijainti [427862.497910265 7211588.40682322] :jatkuvat-havainnot [] :tr-osoite {:tie  8156 :aosa    1 :aet 1015}}
   {:id 273 :sijainti [427862.444927966 7211588.48742365] :jatkuvat-havainnot [] :tr-osoite {:tie  8156 :aosa    1 :aet 1015}}
   {:id 274 :sijainti [427862.372408576 7211588.59774528] :jatkuvat-havainnot [] :tr-osoite {:tie  8156 :aosa    1 :aet 1015}}
   {:id 275 :sijainti [427862.331261608 7211588.66034097] :jatkuvat-havainnot [] :tr-osoite {:tie  8156 :aosa    1 :aet 1015}}
   {:id 276 :sijainti [427862.313950364 7211588.68667606] :jatkuvat-havainnot [] :tr-osoite {:tie  8156 :aosa    1 :aet 1015}}
   {:id 277 :sijainti [427862.298629755 7211588.70998286] :jatkuvat-havainnot [] :tr-osoite {:tie  8156 :aosa    1 :aet 1015}}
   {:id 278 :sijainti [427860.224238366 7211591.61699723] :jatkuvat-havainnot [] :tr-osoite {:tie  8156 :aosa    1 :aet 1018}}
   {:id 279 :sijainti [427859.167264801 7211591.45290308] :jatkuvat-havainnot [] :tr-osoite {:tie  8156 :aosa    1 :aet 1018}}
   {:id 280 :sijainti [427857.625820685 7211591.16088913] :jatkuvat-havainnot [] :tr-osoite {:tie  8156 :aosa    1 :aet 1017}}
   {:id 281 :sijainti [427857.037445367 7211586.99890335] :jatkuvat-havainnot [] :tr-osoite {:tie  8156 :aosa    1 :aet 1013}}
   {:id 282 :sijainti [427860.628583231 7211583.48038123] :jatkuvat-havainnot [] :tr-osoite {:tie  8156 :aosa    1 :aet 1010}}
   {:id 283 :sijainti [427865.08733417 7211582.80012866] :jatkuvat-havainnot [] :tr-osoite {:tie  8156 :aosa    1 :aet 1010}}
   {:id 284 :sijainti [427869.043934927 7211579.31961986] :jatkuvat-havainnot [] :tr-osoite {:tie  8156 :aosa    1 :aet 1007}}
   {:id 285 :sijainti [427868.655287564 7211579.28237671] :jatkuvat-havainnot [] :tr-osoite {:tie  8156 :aosa    1 :aet 1006}}
   {:id 286 :sijainti [427868.586773041 7211579.27581112] :jatkuvat-havainnot [] :tr-osoite {:tie  8156 :aosa    1 :aet 1006}}
   {:id 287 :sijainti [427867.497392443 7211563.32724619] :jatkuvat-havainnot [] :tr-osoite {:tie  8156 :aosa    1 :aet  991}}
   {:id 288 :sijainti [427862.257119912 7211539.76984538] :jatkuvat-havainnot [] :tr-osoite {:tie  8156 :aosa    1 :aet  967}}
   {:id 289 :sijainti [427862.286713951 7211536.40554725] :jatkuvat-havainnot [] :tr-osoite {:tie  8156 :aosa    1 :aet  964}}
   {:id 290 :sijainti [427852.02905823 7211509.48759201] :jatkuvat-havainnot [] :tr-osoite {:tie  8156 :aosa    1 :aet  936}}
   {:id 291 :sijainti [427842.872765103 7211496.36127095] :jatkuvat-havainnot [] :tr-osoite {:tie  8156 :aosa    1 :aet  922}}
   {:id 292 :sijainti [427842.335172337 7211493.74159402] :jatkuvat-havainnot [] :tr-osoite {:tie  8156 :aosa    1 :aet  918}}
   {:id 293 :sijainti [427828.213320904 7211499.3632492] :jatkuvat-havainnot [] :tr-osoite {:tie  8156 :aosa    1 :aet  924}}
   {:id 294 :sijainti [427824.70213651 7211510.80510921] :jatkuvat-havainnot [] :tr-osoite {:tie  8156 :aosa    1 :aet  935}}
   {:id 295 :sijainti [427823.096104622 7211508.96173474] :jatkuvat-havainnot [] :tr-osoite {:tie  8156 :aosa    1 :aet  933}}
   {:id 296 :sijainti [427822.123082624 7211541.03641079] :jatkuvat-havainnot [] :tr-osoite {:tie  8156 :aosa    1 :aet  965}}
   {:id 297 :sijainti [427821.670884597 7211565.60799368] :jatkuvat-havainnot [] :tr-osoite {:tie  8156 :aosa    1 :aet  989}}
   {:id 298 :sijainti [427821.989605054 7211590.44919794] :jatkuvat-havainnot [] :tr-osoite {:tie  8156 :aosa    1 :aet 1014}}
   {:id 299 :sijainti [427823.001213062 7211614.5574108] :jatkuvat-havainnot [] :tr-osoite {:tie  8156 :aosa    1 :aet 1038}}
   {:id 300 :sijainti [427824.76419464 7211639.12924612] :jatkuvat-havainnot [] :tr-osoite {:tie  8156 :aosa    1 :aet 1063}}
   {:id 301 :sijainti [427824.933131664 7211639.16463088] :jatkuvat-havainnot [] :tr-osoite {:tie  8156 :aosa    1 :aet 1063}}
   {:id 302 :sijainti [427827.836528687 7211678.04788383] :jatkuvat-havainnot [] :tr-osoite {:tie  8156 :aosa    1 :aet 1102}}
   {:id 303 :sijainti [427829.953078186 7211705.58748483] :jatkuvat-havainnot [] :tr-osoite {:tie  8156 :aosa    1 :aet 1130}}
   {:id 304 :sijainti [427830.28181687 7211706.92264784] :jatkuvat-havainnot [] :tr-osoite {:tie  8156 :aosa    1 :aet 1131}}
   {:id 305 :sijainti [427833.861693937 7211745.25185903] :jatkuvat-havainnot [] :tr-osoite {:tie  8156 :aosa    1 :aet 1169}}
   {:id 306 :sijainti [427836.391356212 7211773.00323107] :jatkuvat-havainnot [] :tr-osoite {:tie  8156 :aosa    1 :aet 1197}}
   {:id 307 :sijainti [427836.916688242 7211775.42155866] :jatkuvat-havainnot [] :tr-osoite {:tie  8156 :aosa    1 :aet 1200}}
   {:id 308 :sijainti [427840.102291161 7211816.29685431] :jatkuvat-havainnot [] :tr-osoite {:tie  8156 :aosa    1 :aet 1241}}
   {:id 309 :sijainti [427842.308947449 7211845.58922486] :jatkuvat-havainnot [] :tr-osoite {:tie  8156 :aosa    1 :aet 1270}}
   {:id 310 :sijainti [427842.894485067 7211847.85921696] :jatkuvat-havainnot [] :tr-osoite {:tie  8156 :aosa    1 :aet 1272}}
   {:id 311 :sijainti [427846.641854658 7211886.76993187] :jatkuvat-havainnot [] :tr-osoite {:tie  8156 :aosa    1 :aet 1311}}
   {:id 312 :sijainti [427852.299689512 7211913.87643234] :jatkuvat-havainnot [] :tr-osoite {:tie  8156 :aosa    1 :aet 1340}}
   {:id 313 :sijainti [427852.688017866 7211915.9123236] :jatkuvat-havainnot [] :tr-osoite {:tie  8156 :aosa    1 :aet 1342}}
   {:id 314 :sijainti [427865.476455437 7211954.82885717] :jatkuvat-havainnot [] :tr-osoite {:tie  8156 :aosa    1 :aet 1383}}
   {:id 315 :sijainti [427874.506014473 7211980.48261008] :jatkuvat-havainnot [] :tr-osoite {:tie  8156 :aosa    1 :aet 1410}}
   {:id 316 :sijainti [427885.298529886 7212008.45087529] :jatkuvat-havainnot [] :tr-osoite {:tie  8156 :aosa    1 :aet 1440}}
   {:id 317 :sijainti [427894.187836921 7212032.83276721] :jatkuvat-havainnot [] :tr-osoite {:tie  8156 :aosa    1 :aet 1466}}
   {:id 318 :sijainti [427902.617665672 7212056.89169237] :jatkuvat-havainnot [] :tr-osoite {:tie  8156 :aosa    1 :aet 1492}}
   {:id 319 :sijainti [427903.180182773 7212057.62783326] :jatkuvat-havainnot [] :tr-osoite {:tie  8156 :aosa    1 :aet 1493}}
   {:id 320 :sijainti [427915.903775843 7212094.35818396] :jatkuvat-havainnot [] :tr-osoite {:tie  8156 :aosa    1 :aet 1532}}
   {:id 321 :sijainti [427924.333224306 7212118.12347623] :jatkuvat-havainnot [] :tr-osoite {:tie  8156 :aosa    1 :aet 1557}}
   {:id 322 :sijainti [427925.060068413 7212119.85657524] :jatkuvat-havainnot [] :tr-osoite {:tie  8156 :aosa    1 :aet 1559}}
   {:id 323 :sijainti [427934.353750851 7212146.03254657] :jatkuvat-havainnot [] :tr-osoite {:tie  8156 :aosa    1 :aet 1586}}
   {:id 324 :sijainti [427940.984045873 7212163.06942453] :jatkuvat-havainnot [] :tr-osoite {:tie  8156 :aosa    1 :aet 1605}}
   {:id 325 :sijainti [427942.247330346 7212165.96850469] :jatkuvat-havainnot [] :tr-osoite {:tie  8156 :aosa    1 :aet 1608}}
   {:id 326 :sijainti [427948.577419238 7212180.88539194] :jatkuvat-havainnot [] :tr-osoite {:tie  8156 :aosa    1 :aet 1624}}
   {:id 327 :sijainti [427950.913663313 7212186.85269545] :jatkuvat-havainnot [] :tr-osoite {:tie  8156 :aosa    1 :aet 1631}}
   {:id 328 :sijainti [427951.741067549 7212188.82126358] :jatkuvat-havainnot [] :tr-osoite {:tie  8156 :aosa    1 :aet 1633}}
   {:id 329 :sijainti [427952.414487683 7212190.57640489] :jatkuvat-havainnot [] :tr-osoite {:tie  8156 :aosa    1 :aet 1635}}
   {:id 330 :sijainti [427952.924346577 7212191.90525498] :jatkuvat-havainnot [] :tr-osoite {:tie  8156 :aosa    1 :aet 1636}}
   {:id 331 :sijainti [427953.179471238 7212192.57018882] :jatkuvat-havainnot [] :tr-osoite {:tie  8156 :aosa    1 :aet 1637}}
   {:id 332 :sijainti [427953.222395843 7212192.68206362] :jatkuvat-havainnot [] :tr-osoite {:tie  8156 :aosa    1 :aet 1637}}
   {:id 333 :sijainti [427953.222395843 7212192.68206362] :jatkuvat-havainnot [1] :tr-osoite {:tie  8156 :aosa    1 :aet 1637}}
   {:id 334 :sijainti [427953.412881154 7212193.1785273] :jatkuvat-havainnot [1] :tr-osoite {:tie  8156 :aosa    1 :aet 1637}}
   {:id 335 :sijainti [427953.412881154 7212193.1785273] :jatkuvat-havainnot [1] :kitkamittaus 0.55 :tr-osoite {:tie   8156 :aosa    1 :aet 1637}}
   {:id 336 :sijainti [427953.506556277 7212193.42267367] :jatkuvat-havainnot [1] :tr-osoite {:tie  8156 :aosa    1 :aet 1638}}
   {:id 337 :sijainti [427953.543431187 7212193.5187811] :jatkuvat-havainnot [1] :tr-osoite {:tie  8156 :aosa    1 :aet 1638}}
   {:id 338 :sijainti [427953.584096949 7212193.62476866] :jatkuvat-havainnot [1] :tr-osoite {:tie  8156 :aosa    1 :aet 1638}}
   {:id 339 :sijainti [427953.60698595 7212193.68442448] :jatkuvat-havainnot [1] :tr-osoite {:tie  8156 :aosa    1 :aet 1638}}
   {:id 340 :sijainti [427953.620724369 7212193.72023105] :jatkuvat-havainnot [1] :tr-osoite {:tie  8156 :aosa    1 :aet 1638}}
   {:id 341 :sijainti [427953.627023041 7212193.73664734] :jatkuvat-havainnot [1] :tr-osoite {:tie  8156 :aosa    1 :aet 1638}}
   {:id 342 :sijainti [427955.732576961 7212199.05878488] :jatkuvat-havainnot [1] :tr-osoite {:tie  8156 :aosa    1 :aet 1644}}
   {:id 343 :sijainti [427959.900686247 7212208.79164019] :jatkuvat-havainnot [1] :tr-osoite {:tie  8156 :aosa    1 :aet 1654}}
   {:id 344 :sijainti [427960.264788774 7212209.38275162] :jatkuvat-havainnot [1] :tr-osoite {:tie  8156 :aosa    1 :aet 1655}}
   {:id 345 :sijainti [427970.386196041 7212243.48801296] :jatkuvat-havainnot [1] :tr-osoite {:tie  8156 :aosa    1 :aet 1690}}
   {:id 346 :sijainti [427977.684557457 7212270.77573777] :jatkuvat-havainnot [1] :tr-osoite {:tie  8156 :aosa    1 :aet 1719}}
   {:id 347 :sijainti [427978.833889209 7212272.41265525] :jatkuvat-havainnot [1] :tr-osoite {:tie  8156 :aosa    1 :aet 1721}}
   {:id 348 :sijainti [427981.949439297 7212310.45597035] :jatkuvat-havainnot [1] :tr-osoite {:tie  8156 :aosa    1 :aet 1759}}
   {:id 349 :sijainti [427982.113554459 7212337.50904146] :jatkuvat-havainnot [1] :tr-osoite {:tie  8156 :aosa    1 :aet 1786}}
   {:id 350 :sijainti [427982.984361735 7212339.49456552] :jatkuvat-havainnot [1] :tr-osoite {:tie  8156 :aosa    1 :aet 1787}}
   {:id 351 :sijainti [427978.08668122 7212373.44095883] :jatkuvat-havainnot [1] :tr-osoite {:tie  8156 :aosa    1 :aet 1821}}
   {:id 352 :sijainti [427980.50653493 7212396.92399382] :jatkuvat-havainnot [1] :tr-osoite {:tie  8156 :aosa    1 :aet 1842}}
   {:id 353 :sijainti [427981.445056153 7212409.03766766] :jatkuvat-havainnot [1] :tr-osoite {:tie 28417 :aosa   23 :aet   38}}
   {:id 354 :sijainti [428002.01728434 7212426.30447894] :jatkuvat-havainnot [1] :tr-osoite {:tie 28417 :aosa   23 :aet   61}}
   {:id 355 :sijainti [428018.249564277 7212440.58731163] :jatkuvat-havainnot [1] :tr-osoite {:tie 28417 :aosa   23 :aet   81}}
   {:id 356 :sijainti [428025.418402317 7212447.23591629] :jatkuvat-havainnot [1] :tr-osoite {:tie 28417 :aosa   23 :aet   91}}
   {:id 357 :sijainti [428038.837367641 7212472.54631371] :jatkuvat-havainnot [1] :tr-osoite {:tie 28417 :aosa   23 :aet  117}}
   {:id 358 :sijainti [428055.099254213 7212484.29286284] :jatkuvat-havainnot [1] :tr-osoite {:tie    20 :aosa    1 :aet 1013}}
   {:id 359 :sijainti [428055.07956487 7212485.25506098] :jatkuvat-havainnot [1] :tr-osoite {:tie    20 :aosa    1 :aet 1012}}
   {:id 360 :sijainti [428090.09725197 7212481.27593152] :jatkuvat-havainnot [1] :tr-osoite {:tie    20 :aosa    1 :aet 1047}}
   {:id 361 :sijainti [428118.68582191 7212477.78847296] :jatkuvat-havainnot [1] :tr-osoite {:tie    20 :aosa    1 :aet 1076}}
   {:id 362 :sijainti [428118.867604067 7212478.24317844] :jatkuvat-havainnot [1] :tr-osoite {:tie    20 :aosa    1 :aet 1076}}
   {:id 363 :sijainti [428168.089165237 7212473.46190595] :jatkuvat-havainnot [1] :tr-osoite {:tie    20 :aosa    1 :aet 1125}}
   {:id 364 :sijainti [428168.089165237 7212473.46190595] :jatkuvat-havainnot [] :tr-osoite {:tie    20 :aosa    1 :aet 1125}}
   {:id 365 :sijainti [428204.798364122 7212469.17548174] :jatkuvat-havainnot [] :tr-osoite {:tie    20 :aosa    1 :aet 1162}}
   {:id 366 :sijainti [428238.908742669 7212464.66938103] :jatkuvat-havainnot [] :tr-osoite {:tie    20 :aosa    1 :aet 1196}}
   {:id 367 :sijainti [428271.473899756 7212460.24495104] :jatkuvat-havainnot [] :tr-osoite {:tie    20 :aosa    1 :aet 1229}}
   {:id 368 :sijainti [428302.668478292 7212455.30295547] :jatkuvat-havainnot [] :tr-osoite {:tie    20 :aosa    1 :aet 1261}}
   {:id 369 :sijainti [428316.459148877 7212452.99938574] :jatkuvat-havainnot [] :tr-osoite {:tie    20 :aosa    1 :aet 1275}}
   {:id 370 :sijainti [428359.425771073 7212444.84733847] :jatkuvat-havainnot [] :tr-osoite {:tie    20 :aosa    1 :aet 1319}}
   {:id 371 :sijainti [428391.000858254 7212438.60303151] :jatkuvat-havainnot [] :tr-osoite {:tie    20 :aosa    1 :aet 1351}}
   {:id 372 :sijainti [428391.877359322 7212438.11803947] :jatkuvat-havainnot [] :tr-osoite {:tie    20 :aosa    1 :aet 1352}}
   {:id 373 :sijainti [428441.040091921 7212428.34818394] :jatkuvat-havainnot [] :tr-osoite {:tie    20 :aosa    2 :aet 1402}}
   {:id 374 :sijainti [428478.294560258 7212420.41759159] :jatkuvat-havainnot [] :tr-osoite {:tie    20 :aosa    2 :aet 1440}}
   {:id 375 :sijainti [428480.290512612 7212419.61607645] :jatkuvat-havainnot [] :tr-osoite {:tie    20 :aosa    2 :aet 1442}}
   {:id 376 :sijainti [428530.135443247 7212409.52745182] :jatkuvat-havainnot [] :tr-osoite {:tie    20 :aosa    2 :aet 1493}}
   {:id 377 :sijainti [428566.834039368 7212401.75590155] :jatkuvat-havainnot [] :tr-osoite {:tie    20 :aosa    2 :aet 1531}}
   {:id 378 :sijainti [428569.754107113 7212400.59407513] :jatkuvat-havainnot [] :tr-osoite {:tie    20 :aosa    2 :aet 1534}}
   {:id 379 :sijainti [428620.588653287 7212389.85190477] :jatkuvat-havainnot [] :tr-osoite {:tie    20 :aosa    2 :aet 1586}}
   {:id 380 :sijainti [428657.678390485 7212380.80588218] :jatkuvat-havainnot [] :tr-osoite {:tie    20 :aosa    2 :aet 1624}}
   {:id 381 :sijainti [428660.757699763 7212379.61118152] :jatkuvat-havainnot [] :tr-osoite {:tie    20 :aosa    2 :aet 1627}}
   {:id 382 :sijainti [428710.030181011 7212362.34251793] :jatkuvat-havainnot [] :tr-osoite {:tie    20 :aosa    2 :aet 1680}}
   {:id 383 :sijainti [428744.557188104 7212348.45801753] :jatkuvat-havainnot [] :tr-osoite {:tie    20 :aosa    2 :aet 1717}}
   {:id 384 :sijainti [428747.527283532 7212347.02498264] :jatkuvat-havainnot [] :tr-osoite {:tie    20 :aosa    2 :aet 1720}}
   {:id 385 :sijainti [428794.843680854 7212328.14075168] :jatkuvat-havainnot [] :tr-osoite {:tie    20 :aosa    2 :aet 1771}}
   {:id 386 :sijainti [428829.286407014 7212316.9072402] :jatkuvat-havainnot [] :tr-osoite {:tie    20 :aosa    2 :aet 1808}}
   {:id 387 :sijainti [428832.044440994 7212315.03253453] :jatkuvat-havainnot [] :tr-osoite {:tie    20 :aosa    2 :aet 1811}}
   {:id 388 :sijainti [428879.809403825 7212307.99473842] :jatkuvat-havainnot [] :tr-osoite {:tie    20 :aosa    2 :aet 1859}}
   {:id 389 :sijainti [428911.967408535 7212305.00281987] :jatkuvat-havainnot [] :tr-osoite {:tie    20 :aosa    2 :aet 1891}}
   {:id 390 :sijainti [428915.097560016 7212303.84946191] :jatkuvat-havainnot [] :tr-osoite {:tie    20 :aosa    2 :aet 1894}}
   {:id 391 :sijainti [428912.152969531 7212304.38913567] :jatkuvat-havainnot [] :tr-osoite {:tie    20 :aosa    2 :aet 1891}}
   {:id 392 :sijainti [428907.936654583 7212305.16188644] :jatkuvat-havainnot [] :tr-osoite {:tie    20 :aosa    2 :aet 1887}}
   {:id 393 :sijainti [428964.86046763 7212298.16173012] :jatkuvat-havainnot [] :tr-osoite {:tie    20 :aosa    2 :aet 1944}}
   {:id 394 :sijainti [428992.960638489 7212297.81164406] :jatkuvat-havainnot [] :tr-osoite {:tie    20 :aosa    2 :aet 1974}}
   {:id 395 :sijainti [429005.115600712 7212297.66021143] :jatkuvat-havainnot [] :tr-osoite {:tie    20 :aosa    2 :aet 1986}}
   {:id 396 :sijainti [429011.484291979 7212297.58086707] :jatkuvat-havainnot [] :tr-osoite {:tie    20 :aosa    2 :aet 1992}}
   {:id 397 :sijainti [429015.156894799 7212297.53511194] :jatkuvat-havainnot [] :tr-osoite {:tie    20 :aosa    2 :aet 1996}}
   {:id 398 :sijainti [429017.392594399 7212297.50725847] :jatkuvat-havainnot [] :tr-osoite {:tie    20 :aosa    2 :aet 1998}}
   {:id 399 :sijainti [429005.115600712 7212297.66021143] :jatkuvat-havainnot [] :pistemainen-havainto 5 :kuvaus "Foo" :tr-osoite {:tie    20 :aosa   1 :aet 1986}}
   {:id 400 :sijainti [429017.736167795 7212298.84444803] :jatkuvat-havainnot [] :tr-osoite {:tie    20 :aosa    2 :aet 1998}}
   {:id 401 :sijainti [429017.868095822 7212299.35791264] :jatkuvat-havainnot [] :tr-osoite {:tie    20 :aosa    2 :aet 1998}}
   {:id 402 :sijainti [429017.961761517 7212299.72246004] :jatkuvat-havainnot [] :tr-osoite {:tie    20 :aosa    2 :aet 1998}}
   {:id 403 :sijainti [429018.02695737 7212299.97620266] :jatkuvat-havainnot [] :tr-osoite {:tie    20 :aosa    2 :aet 1998}}
   {:id 404 :sijainti [429028.787623701 7212299.68024129] :jatkuvat-havainnot [] :tr-osoite {:tie    20 :aosa    2 :aet 2009}}
   {:id 405 :sijainti [429034.304426639 7212299.75809746] :jatkuvat-havainnot [] :tr-osoite {:tie    20 :aosa    2 :aet 2014}}
   {:id 406 :sijainti [429060.763180798 7212294.62587648] :jatkuvat-havainnot [] :tr-osoite {:tie    20 :aosa    2 :aet 2041}}
   {:id 407 :sijainti [429080.687783642 7212290.66822956] :jatkuvat-havainnot [] :tr-osoite {:tie    20 :aosa    2 :aet 2061}}
   {:id 408 :sijainti [429089.638320199 7212288.87461877] :jatkuvat-havainnot [] :tr-osoite {:tie    20 :aosa    2 :aet 2070}}
   {:id 409 :sijainti [429119.844351259 7212281.71454976] :jatkuvat-havainnot [] :tr-osoite {:tie    20 :aosa    2 :aet 2101}}
   {:id 410 :sijainti [429144.328500481 7212274.58768253] :jatkuvat-havainnot [] :tr-osoite {:tie    20 :aosa    2 :aet 2127}}
   {:id 411 :sijainti [429155.629750466 7212271.09771481] :jatkuvat-havainnot [] :tr-osoite {:tie    20 :aosa    2 :aet 2139}}
   {:id 412 :sijainti [429187.781689371 7212263.22110312] :jatkuvat-havainnot [] :tr-osoite {:tie    20 :aosa    2 :aet 2172}}
   {:id 413 :sijainti [429208.407432788 7212257.83520731] :jatkuvat-havainnot [] :tr-osoite {:tie    20 :aosa    2 :aet 2193}}
   {:id 414 :sijainti [429218.735903723 7212254.86697167] :jatkuvat-havainnot [] :tr-osoite {:tie    20 :aosa    2 :aet 2204}}
   {:id 415 :sijainti [429227.379035086 7212236.68485274] :jatkuvat-havainnot [] :tr-osoite {:tie 70020 :aosa  803 :aet  234}}
   {:id 416 :sijainti [429229.978976883 7212223.35465473] :jatkuvat-havainnot [] :tr-osoite {:tie 70020 :aosa  803 :aet  239}}
   {:id 417 :sijainti [429231.233442783 7212222.99010953] :jatkuvat-havainnot [] :tr-osoite {:tie 70020 :aosa  803 :aet  241}}
   {:id 418 :sijainti [429243.808984453 7212210.00669027] :jatkuvat-havainnot [] :tr-osoite {:tie 70020 :aosa  803 :aet  252}}
   {:id 419 :sijainti [429254.793967263 7212201.3340916] :jatkuvat-havainnot [] :tr-osoite {:tie 70020 :aosa  803 :aet  265}}
   {:id 420 :sijainti [429254.554404055 7212199.89964597] :jatkuvat-havainnot [] :tr-osoite {:tie 70020 :aosa  803 :aet  265}}
   {:id 421 :sijainti [429271.684079176 7212192.72033411] :jatkuvat-havainnot [] :tr-osoite {:tie 70020 :aosa  803 :aet  283}}
   {:id 422 :sijainti [429282.47889398 7212193.57214931] :jatkuvat-havainnot [] :tr-osoite {:tie 70020 :aosa  803 :aet  292}}
   {:id 423 :sijainti [429283.610966808 7212192.18736173] :jatkuvat-havainnot [] :tr-osoite {:tie 70020 :aosa  803 :aet  292}}
   {:id 424 :sijainti [429288.203799325 7212200.54391295] :jatkuvat-havainnot [] :tr-osoite {:tie 70020 :aosa  803 :aet  296}}
   {:id 425 :sijainti [429287.284319636 7212207.95619924] :jatkuvat-havainnot [] :tr-osoite {:tie 70020 :aosa  803 :aet  295}}
   {:id 426 :sijainti [429288.171113663 7212208.79259137] :jatkuvat-havainnot [] :tr-osoite {:tie 70020 :aosa  803 :aet  296}}
   {:id 427 :sijainti [429286.097344097 7212211.24060132] :jatkuvat-havainnot [] :tr-osoite {:tie 70020 :aosa  803 :aet  293}}
   {:id 428 :sijainti [429285.750291109 7212212.25598482] :jatkuvat-havainnot [] :tr-osoite {:tie 70020 :aosa  803 :aet  293}}
   {:id 429 :sijainti [429285.568220613 7212212.78867402] :jatkuvat-havainnot [] :tr-osoite {:tie 70020 :aosa  803 :aet  293}}
   {:id 430 :sijainti [427333.6309061 7211245.39131675] :jatkuvat-havainnot [] :tr-osoite {:tie  8156 :aosa    1 :aet  716}}
   {:id 431 :sijainti [427339.028489113 7211241.4734603] :jatkuvat-havainnot [] :tr-osoite {:tie  8156 :aosa    1 :aet  714}}
   {:id 432 :sijainti [427340.145230426 7211240.66286931] :jatkuvat-havainnot [] :tr-osoite {:tie  8156 :aosa    1 :aet  714}}
   {:id 433 :sijainti [427341.095648565 7211239.97300464] :jatkuvat-havainnot [] :tr-osoite {:tie  8156 :aosa    1 :aet  714}}
   {:id 434 :sijainti [427341.804388948 7211239.4585627] :jatkuvat-havainnot [] :tr-osoite {:tie  8156 :aosa    1 :aet  714}}])
