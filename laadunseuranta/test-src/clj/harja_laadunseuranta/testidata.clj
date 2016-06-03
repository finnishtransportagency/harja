(ns harja-laadunseuranta.testidata
  "Testidataa yksikkötestejä varten"
  (:require [clojure.test :as t]))

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
