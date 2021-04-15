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
            [clojure.core.match :refer [match]]
            [harja.palvelin.komponentit.pdf-vienti :as pdf-vienti]
            [harja.palvelin.raportointi :as raportointi]
            [harja.palvelin.palvelut.raportit :as raportit]
            [clojure.string :as str]))

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
    (is (match vastaus
               [:raportti
                 {:nimi "Määräaikaan mennessä tehtävät työt"
                  :orientaatio :landscape}
                 [:taulukko
                  {:otsikko (_ :guard #(str/starts-with? % "Oulun alueurakka 2014-2019, Määräaikaan mennessä tehtävät työt"))
                   :sheet-nimi "Määräaikaan mennessä tehtävät työt"
                   :tyhja nil}
                  [{:leveys 10
                    :otsikko "Työn kuvaus"}
                   {:leveys 5
                    :otsikko "Takaraja"}
                   {:leveys 10
                    :otsikko "Valmistunut"}
                   {:leveys 10
                    :otsikko "Kommentti"}]
                  [{:otsikko "Ajoissa valmistuneet (25 %)"}
                   ["Koko urakan alue aurattu"
                    "29.05.2014"
                    (_ :guard #(str/starts-with? % "01.05.2014"))
                    "Homma hoidettu hyvästi ennen tavoitepäivää!"]
                   {:otsikko "Myöhässä valmistuneet (25 %)"}
                   ["Pelkosentie 678 suolattu"
                    "23.09.2015"
                    (_ :guard #(str/starts-with? % "25.09.2015"))
                    "Aurattu, mutta vähän tuli myöhässä"]
                   {:otsikko "Kesken (25 %)"}
                   ["Oulaisten liikenneympyrä aurattu"
                    (_ :guard #(str/starts-with? % "01.01.2050"))
                    "-"
                    nil]
                   {:otsikko "Valmistumatta (25 %)"}
                   ["Sepon mökkitie suolattu"
                    (_ :guard #(str/starts-with? % "24.12.2014"))
                    "-"
                    nil]]]]
               true))))