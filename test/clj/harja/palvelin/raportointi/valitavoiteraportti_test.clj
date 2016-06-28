(ns harja.palvelin.raportointi.valitavoiteraportti-test
  (:require [clojure.test :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.toimenpidekoodit :refer :all]
            [harja.palvelin.palvelut.urakat :refer :all]
            [harja.kyselyt.urakat :as urk-q]
            [harja.testi :refer :all]
            [taoensso.timbre :as log]
            [com.stuartsierra.component :as component]
            [harja.pvm :as pvm]
            [clj-time.core :as t]
            [clj-time.coerce :as c]
            [harja.palvelin.komponentit.pdf-vienti :as pdf-vienti]
            [harja.palvelin.raportointi :as raportointi]
            [harja.palvelin.palvelut.raportit :as raportit]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :pdf-vienti (component/using
                                      (pdf-vienti/luo-pdf-vienti)
                                      [:http-palvelin])
                        :raportointi (component/using
                                       (raportointi/luo-raportointi)
                                       [:db :pdf-vienti])
                        :raportit (component/using
                                    (raportit/->Raportit)
                                    [:http-palvelin :db :raportointi :pdf-vienti])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :once (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))


(deftest raportin-suoritus-urakalle-toimii
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi :valitavoiteraportti
                                 :konteksti "urakka"
                                 :urakka-id (hae-oulun-alueurakan-2014-2019-id)})]
    (is (vector? vastaus))
    (is (= vastaus [:raportti
                    {:nimi "Välitavoiteraportti"
                     :orientaatio :landscape}
                    [:taulukko
                     {:otsikko "Oulun alueurakka 2014-2019, Välitavoiteraportti, suoritettu 28.06.2016"
                      :sheet-nimi "Välitavoiteraportti"
                      :tyhja nil}
                     [{:leveys 10
                       :otsikko "Välitavoite"}
                      {:leveys 5
                       :otsikko "Takaraja"}
                      {:leveys 10
                       :otsikko "Valmistunut"}
                      {:leveys 10
                       :otsikko "Kommentti"}]
                     [{:otsikko "Ajoissa valmistuneet (25%)"}
                      ["Koko Suomi aurattu"
                       "29.05.2014"
                       "01.05.2014 (1 kuukausi ennen)"
                       "Homma hoidettu hyvästi ennen tavoitepäivää!"]
                      {:otsikko "Myöhässä valmistuneet (25%)"}
                      ["Pelkosentie 678 suolattu"
                       "23.09.2015"
                       "25.09.2015 (2 päivää myöhässä)"
                       "Aurattu, mutta vähän tuli myöhässä"]
                      {:otsikko "Kesken (25%)"}
                      ["Oulaisten liikenneympyrä aurattu"
                       "01.01.2050 (36 vuotta jäljellä)"
                       "-"
                       nil]
                      {:otsikko "Valmistumatta (25%)"}
                      ["Sepon mökkitie suolattu"
                       "24.12.2014 (1 vuosi myöhässä)"
                       "-"
                       nil]]]]))))