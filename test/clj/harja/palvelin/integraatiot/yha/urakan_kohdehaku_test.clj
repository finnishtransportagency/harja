(ns harja.palvelin.integraatiot.yha.urakan-kohdehaku-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [com.stuartsierra.component :as component]
            [org.httpkit.fake :refer [with-fake-http]]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.yha.yha-komponentti :as yha]
            [harja.palvelin.palvelut.yha :as yha-palvelu]
            [harja.palvelin.integraatiot.yha.tyokalut :refer :all])
  (:use [slingshot.slingshot :only [try+]]))

(def kayttaja "jvh")

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea
    kayttaja
    :yha (component/using
           (yha/->Yha {:url +yha-url+})
           [:db :http-palvelin :integraatioloki])))

(use-fixtures :each jarjestelma-fixture)

(defn hae-urakka-id []
  (first (first (q "SELECT id FROM urakka WHERE nimi = 'YHA-päällystysurakka (sidottu)';"))))

(defn hae-yha-id [urakka-id]
  (first (first (q (format "SELECT yhaid\nFROM yhatiedot\nWHERE urakka = %s;" urakka-id)))))

(def urakan-kohteet-url (str +yha-url+ "haeUrakanKohteet"))

(deftest tarkista-urakan-kohteiden-haku
  (let [urakka-id (hae-urakka-id)
        odotettu-vastaus [{:alikohteet (mapv #(assoc % :nykyinen-paallyste 1
                                                       :yllapitoluokka 1
                                                       :keskimaarainen-vuorokausiliikenne 1000)
                                             [{:paallystystoimenpide {:kokonaismassamaara 124.0
                                                                      :kuulamylly 4
                                                                      :paallystetyomenetelma 22
                                                                      :raekoko 12
                                                                      :rc-prosentti 14
                                                                      :uusi-paallyste 11}
                                               :tierekisteriosoitevali {:aet 3
                                                                        :ajorata 1
                                                                        :aosa 101
                                                                        :kaista 11
                                                                        :karttapaivamaara #inst "2015-12-31T22:00:00.000-00:00"
                                                                        :let 30
                                                                        :losa 101
                                                                        :tienumero 4}
                                               :tunnus nil
                                               :yha-id 3}
                                              {:paallystystoimenpide {:kokonaismassamaara 124.0
                                                                      :kuulamylly 4
                                                                      :paallystetyomenetelma 22
                                                                      :raekoko 12
                                                                      :rc-prosentti 14
                                                                      :uusi-paallyste 11}
                                               :tierekisteriosoitevali {:aet 30
                                                                        :ajorata 1
                                                                        :aosa 101
                                                                        :kaista 11
                                                                        :karttapaivamaara #inst "2015-12-31T22:00:00.000-00:00"
                                                                        :let 300
                                                                        :losa 101
                                                                        :tienumero 4}
                                               :tunnus nil
                                               :yha-id 4}])
                           :nimi "string"
                           :tierekisteriosoitevali {:aet 3
                                                    :aosa 101
                                                    :karttapaivamaara #inst "2015-12-31T22:00:00.000-00:00"
                                                    :let 300
                                                    :losa 101
                                                    :tienumero 4}
                           :tunnus "A"
                           :yha-id 3
                           :yha-kohdenumero 666
                           :yllapitokohdetyotyyppi :paikkaus
                           :yllapitokohdetyyppi "paallyste"}]
        [od_id, od_yllapitokohde, od_nimi, od_tr_numero, od_tr_alkuosa, od_tr_alkuetaisyys, od_tr_loppuosa, od_tr_loppuetaisyys, od_poistettu, od_yhaid, od_tr_ajorata, od_tr_kaista, od_toimenpide, od_ulkoinen_id, od_paallystetyyppi, od_raekoko, od_tyomenetelma, od_massamaara, od_muokattu, od_keskimaarainen_vuorokausiliikenne, od_yllapitoluokka, od_nykyinen_paallyste] [44 31 nil 4 101 3 101 30 false 3 1 11 nil nil nil nil nil nil nil nil 1 nil]
        url urakan-kohteet-url]
    (with-fake-http [url +onnistunut-urakan-kohdehakuvastaus+]
                    (let [vastaus (yha/hae-kohteet (:yha jarjestelma) urakka-id "testi")
                          db (luo-testitietokanta)
                          kohteet-validointitiedoilla (yha-palvelu/lisaa-kohteisiin-validointitiedot db vastaus)
                          validit-kohteet (filter :kohde-validi? kohteet-validointitiedoilla)]

                      (doseq [kohde validit-kohteet]
                        (yha-palvelu/tallenna-kohde-ja-alikohteet db urakka-id kohde))
                      (let [[id, yllapitokohde, nimi, tr_numero, tr_alkuosa, tr_alkuetaisyys, tr_loppuosa, tr_loppuetaisyys, poistettu, yhaid, tr_ajorata, tr_kaista, toimenpide, ulkoinen_id, paallystetyyppi, raekoko, tyomenetelma, massamaara, muokattu, keskimaarainen_vuorokausiliikenne, yllapitoluokka, nykyinen_paallyste] (first (q (str "SELECT id, yllapitokohde, nimi, tr_numero, tr_alkuosa, tr_alkuetaisyys, tr_loppuosa, tr_loppuetaisyys, poistettu, yhaid, tr_ajorata, tr_kaista, toimenpide, ulkoinen_id, paallystetyyppi, raekoko, tyomenetelma, massamaara, muokattu, keskimaarainen_vuorokausiliikenne, yllapitoluokka, nykyinen_paallyste FROM YLLAPITOKOHDEOSA WHERE yhaid IN (3,4);")))]
                        (is (= id od_id) "id")
                        (is (= yllapitokohde od_yllapitokohde) "yllapitokohde")
                        (is (= nimi od_nimi) "nimi")
                        (is (= tr_numero od_tr_numero) "tr_numero")
                        (is (= tr_alkuosa od_tr_alkuosa) "tr_alkuosa")
                        (is (= tr_alkuetaisyys od_tr_alkuetaisyys) "tr_alkuetaisyys")
                        (is (= tr_loppuosa od_tr_loppuosa) "tr_loppuosa")
                        (is (= tr_loppuetaisyys od_tr_loppuetaisyys) "tr_loppuetaisyys")
                        (is (= poistettu od_poistettu) "poistettu")
                        (is (= yhaid od_yhaid) "yhaid")
                        (is (= tr_ajorata od_tr_ajorata) "tr_ajorata")
                        (is (= tr_kaista od_tr_kaista) "tr_kaista")
                        (is (= toimenpide od_toimenpide) "toimenpide")
                        (is (= ulkoinen_id od_ulkoinen_id) "ulkoinen_id")
                        (is (= paallystetyyppi od_paallystetyyppi) "paallystetyyppi")
                        (is (= raekoko od_raekoko) "raekoko")
                        (is (= tyomenetelma od_tyomenetelma) "tyomenetelma")
                        (is (= massamaara od_massamaara) "massamaara")
                        (is (= muokattu od_muokattu) "muokattu")
                        (is (= keskimaarainen_vuorokausiliikenne od_keskimaarainen_vuorokausiliikenne) "keskimaarainen_vuorokausiliikenne")
                        (is (= yllapitoluokka od_yllapitoluokka) "yllapitoluokka")
                        (is (= nykyinen_paallyste od_nykyinen_paallyste) "nykyinen_paallyste"))


        (is (= odotettu-vastaus vastaus))))))

(deftest tarkista-epaonnistunut-kutsu
  (let [urakka-id (hae-urakka-id)
        yha-id (hae-yha-id urakka-id)]
    (with-fake-http [{:url urakan-kohteet-url :method :get} 500]
      (is (thrown? Exception (yha/hae-kohteet (:yha jarjestelma) yha-id ""))
          "Poikkeusta ei heitetty epäonnistuneesta kutsusta."))))

(deftest tarkista-virhevastaus
  (let [urakka-id (hae-urakka-id)]
    (with-fake-http [urakan-kohteet-url +virhevastaus+]
      (try+
        (yha/hae-kohteet (:yha jarjestelma) urakka-id "testi")
        (is false "Poikkeusta ei heitetty epäonnistuneesta kutsusta.")
        (catch [:type yha/+virhe-urakan-kohdehaussa+] {:keys [virheet]}
          (is true "Poikkeus heitettiin epäonnistuneesta kutsusta."))))))