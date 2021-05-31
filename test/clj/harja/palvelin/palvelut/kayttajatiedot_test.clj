(ns harja.palvelin.palvelut.kayttajatiedot-test
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.haku :refer :all]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [harja.palvelin.palvelut.kayttajatiedot :as kayttajatiedot]))


(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :kayttajatiedot (component/using
                                          (kayttajatiedot/->Kayttajatiedot)
                                          [:http-palvelin :db])))))
  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :once jarjestelma-fixture)

(deftest yhteydenpito-vastaanottajat-toimii
  (let [tulos (kutsu-palvelua (:http-palvelin jarjestelma)
                              :yhteydenpito-vastaanottajat +kayttaja-jvh+ nil)]
    (println "vastaanottajat:" tulos)

    (is (= (count tulos) 12))
    (is (= (vec (distinct (mapcat keys tulos))) [:etunimi :sukunimi :sahkoposti]))))

(deftest yhdista-kayttajan-urakat-alueittain
  (let [ely-kaakkoissuomi {:id 7, :nimi "Kaakkois-Suomi", :elynumero 3}
        ely-lappi {:id 1, :nimi "Lappi", :elynumero 678}
        tienpaallystys-1 {:id 18, :nimi "Tienpäällystysurakka KAS ELY 1 2015", :alue nil}
        hoito-1 {:id 1, :nimi "Joku hoidon urakka", :alue nil}
        tienpaallystys-2 {:id 2, :nimi "Joku tienpäällystysjuttu", :alue nil}
        hoito-2 {:id 24, :nimi "Joku hoitourakkajuttu", :alue nil}]

    (let [urakat-a [{:tyyppi :paallystys,
                     :hallintayksikko ely-kaakkoissuomi,
                     :urakat #{tienpaallystys-1}}
                    {:tyyppi :hoito,
                     :hallintayksikko ely-kaakkoissuomi,
                     :urakat #{hoito-1}}]
          urakat-b [{:tyyppi :paallystys,
                     :hallintayksikko ely-kaakkoissuomi,
                     :urakat #{tienpaallystys-2}}]]
      (is (= (kayttajatiedot/yhdista-kayttajan-urakat-alueittain
               urakat-a
               urakat-b)
             [{:tyyppi :paallystys,
               :hallintayksikko ely-kaakkoissuomi,
               :urakat #{tienpaallystys-2
                        tienpaallystys-1}}
              {:tyyppi :hoito,
               :hallintayksikko ely-kaakkoissuomi,
               :urakat #{hoito-1}}])))

    (let [urakat-a [{:tyyppi :paallystys,
                     :hallintayksikko ely-kaakkoissuomi,
                     :urakat #{tienpaallystys-1}}
                    {:tyyppi :hoito,
                     :hallintayksikko ely-kaakkoissuomi,
                     :urakat #{hoito-1}}]
          urakat-b [{:tyyppi :hoito,
                     :hallintayksikko ely-kaakkoissuomi,
                     :urakat #{hoito-2}}]]
      (is (= (kayttajatiedot/yhdista-kayttajan-urakat-alueittain
               urakat-a
               urakat-b)
             [{:tyyppi :paallystys,
               :hallintayksikko ely-kaakkoissuomi,
               :urakat #{tienpaallystys-1}}
              {:tyyppi :hoito,
               :hallintayksikko ely-kaakkoissuomi,
               :urakat #{hoito-2 hoito-1}}])))

    (let [urakat-a [{:tyyppi :paallystys,
                     :hallintayksikko ely-kaakkoissuomi,
                     :urakat #{tienpaallystys-1}}]
          urakat-b [{:tyyppi :paallystys,
                     :hallintayksikko ely-kaakkoissuomi,
                     :urakat #{tienpaallystys-1}}]]
      (is (= (kayttajatiedot/yhdista-kayttajan-urakat-alueittain
               urakat-a
               urakat-b)
             [{:tyyppi :paallystys,
               :hallintayksikko ely-kaakkoissuomi,
               :urakat #{tienpaallystys-1}}])))

    (let [urakat-a [{:tyyppi :paallystys,
                     :hallintayksikko ely-kaakkoissuomi,
                     :urakat #{tienpaallystys-1}}]
          urakat-b [{:tyyppi :paallystys,
                     :hallintayksikko ely-lappi,
                     :urakat #{tienpaallystys-1}}]]
      (is (= (kayttajatiedot/yhdista-kayttajan-urakat-alueittain
               urakat-a
               urakat-b)
             [{:tyyppi :paallystys,
               :hallintayksikko ely-kaakkoissuomi,
               :urakat #{tienpaallystys-1}}
              {:tyyppi :paallystys,
               :hallintayksikko ely-lappi,
               :urakat #{tienpaallystys-1}}])))

    (let [urakat-a [{:tyyppi :paallystys,
                     :hallintayksikko ely-kaakkoissuomi,
                     :urakat #{tienpaallystys-1}}]
          urakat-b [{:tyyppi :paallystys,
                     :hallintayksikko ely-lappi,
                     :urakat #{tienpaallystys-1}}]]
      (is (= (kayttajatiedot/yhdista-kayttajan-urakat-alueittain
               urakat-a
               urakat-b)
             [{:tyyppi :paallystys,
               :hallintayksikko ely-kaakkoissuomi,
               :urakat #{tienpaallystys-1}}
              {:tyyppi :paallystys,
               :hallintayksikko ely-lappi,
               :urakat #{tienpaallystys-1}}])))

    (let [urakat-a []
          urakat-b []]
      (is (= (kayttajatiedot/yhdista-kayttajan-urakat-alueittain
               urakat-a
               urakat-b))
          []))))
