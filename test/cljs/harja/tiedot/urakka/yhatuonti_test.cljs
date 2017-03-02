(ns harja.tiedot.urakka.yhatuonti-test
  (:require [harja.tiedot.urakka.yhatuonti :as yha]
            [cljs.test :as test :refer-macros [deftest is]]
            [harja.loki :refer [log]]))

(def yha-data
  [{:alikohteet [{:paallystystoimenpide
                  {:kokonaismassamaara 124,
                   :kuulamylly 4,
                   :paallystetyomenetelma 22,
                   :raekoko 12,
                   :rc-prosentti 14,
                   :uusi-paallyste 11},
                  :tierekisteriosoitevali {:aet 4,
                                           :ajorata 1,
                                           :aosa 3,
                                           :kaista 11,
                                           :karttapaivamaara nil,
                                           :let 2,
                                           :losa 4,
                                           :tienumero 4},
                  :yha-id 12}],
    :keskimaarainen-vuorokausiliikenne 1000,
    :kohdetyyppi :paikkaus,
    :nykyinen-paallyste 1,
    :tierekisteriosoitevali {:aet 3,
                             :ajorata 0,
                             :aosa 3,
                             :kaista 11,
                             :karttapaivamaara nil,
                             :let 3,
                             :losa 3,
                             :tienumero 3},
    :yha-id 1,
    :yllapitoluokka 1}
   {:keskimaarainen-vuorokausiliikenne 1000,
    :kohdetyyppi :paikkaus,
    :nykyinen-paallyste 1,
    :tierekisteriosoitevali {:aet 3,
                             :ajorata 0,
                             :aosa 3,
                             :kaista 11,
                             :karttapaivamaara nil,
                             :let 3,
                             :losa 3,
                             :tienumero 3},
    :yha-id 2,
    :yllapitoluokka 1}])

(def vkm-data
  {"tieosoitteet" [{"ajorata" 0, "palautusarvo" 1, "osa" 1, "etaisyys" 2, "tie" 20, "tunniste" "kohde-1-alku"}
                   {"ajorata" 0, "palautusarvo" 1, "osa" 3, "etaisyys" 4, "tie" 20, "tunniste" "kohde-1-loppu"}
                   {"ajorata" 0, "palautusarvo" 1, "osa" 5, "etaisyys" 6, "tie" 20, "tunniste" "alikohde-1-12-alku"}
                   {"ajorata" 0, "palautusarvo" 1, "osa" 7, "etaisyys" 8, "tie" 20, "tunniste" "alikohde-1-12-loppu"}
                   {"palautusarvo" 0, "virheteksti" "Tieosoitteelle ei saatu historiatietoa.", "tunniste" "kohde-2-alku"}
                   {"palautusarvo" 0, "virheteksti" "Tieosoitteelle ei saatu historiatietoa.", "tunniste" "kohde-2-loppu"}]})

(deftest tarkista-vkm-ja-yha-osoitteiden-yhdistaminen
  (let [yhdistetty-data (yha/yhdista-yha-ja-vkm-kohteet yha-data vkm-data)
        kohde (first yhdistetty-data)
        alikohde (first (:alikohteet kohde))]
    ;; Kohteen osoitteet
    (is (= 20 (get-in kohde [:tierekisteriosoitevali :tienumero])))
    (is (= 0 (get-in kohde [:tierekisteriosoitevali :ajorata])))
    (is (= 1 (get-in kohde [:tierekisteriosoitevali :aosa])))
    (is (= 2 (get-in kohde [:tierekisteriosoitevali :aet])))
    (is (= 3 (get-in kohde [:tierekisteriosoitevali :losa])))
    (is (= 4 (get-in kohde [:tierekisteriosoitevali :let])))

    ;; Alikohteen osoitteet
    (is (= 20 (get-in alikohde [:tierekisteriosoitevali :tienumero])))
    (is (= 0 (get-in alikohde [:tierekisteriosoitevali :ajorata])))
    (is (= 5 (get-in alikohde [:tierekisteriosoitevali :aosa])))
    (is (= 6 (get-in alikohde [:tierekisteriosoitevali :aet])))
    (is (= 7 (get-in alikohde [:tierekisteriosoitevali :losa])))
    (is (= 8 (get-in alikohde [:tierekisteriosoitevali :let])))))

(deftest tarkista-yhdistaminen-kun-osoitetta-ei-loydy
  (let [yhdistetty-data (yha/yhdista-yha-ja-vkm-kohteet yha-data vkm-data)
        kohde (second yhdistetty-data)]
    ;; Kohteen osoite pitäisi pysyä samana
    (is (= 3 (get-in kohde [:tierekisteriosoitevali :tienumero])))
    (is (= 0 (get-in kohde [:tierekisteriosoitevali :ajorata])))
    (is (= 3 (get-in kohde [:tierekisteriosoitevali :aosa])))
    (is (= 3 (get-in kohde [:tierekisteriosoitevali :aet])))
    (is (= 3 (get-in kohde [:tierekisteriosoitevali :losa])))
    (is (= 3 (get-in kohde [:tierekisteriosoitevali :let])))))

(deftest tarkista-tieosoitteiden-rakentaminen
  (let [oletettu-tulos [{:tunniste "kohde-1-alku", :tie 3, :osa 3, :etaisyys 3, :ajorata 0}
                        {:tunniste "kohde-1-loppu", :tie 3, :osa 3, :etaisyys 3, :ajorata 0}
                        {:tunniste "alikohde-1-12-alku", :tie 4, :osa 3, :etaisyys 4, :ajorata 1}
                        {:tunniste "alikohde-1-12-loppu", :tie 4, :osa 4, :etaisyys 2, :ajorata 1}
                        {:tunniste "kohde-2-alku", :tie 3, :osa 3, :etaisyys 3, :ajorata 0}
                        {:tunniste "kohde-2-loppu", :tie 3, :osa 3, :etaisyys 3, :ajorata 0}]
        tieosoitteet (yha/rakenna-tieosoitteet yha-data)]
    (is (= oletettu-tulos tieosoitteet))))

(deftest tarkista-kohteen-osan-paivittaminen
  (is (= {:tierekisteriosoitevali {:aet 1}}
         (yha/paivita-osoitteen-osa {:tierekisteriosoitevali {:aet 1}} {} :aet "aet")))
  (is (= {:tierekisteriosoitevali {:aet 2}}
         (yha/paivita-osoitteen-osa {:tierekisteriosoitevali {:aet 1}} {"aet" 2} :aet "aet"))))