(ns harja.palvelin.raportointi
  "Raportointimoottorin komponentti ja apurit."
  (:require [com.stuartsierra.component :as component]
            [clojure.java.jdbc :as jdbc]
            [harja.palvelin.komponentit.pdf-vienti :as pdf-vienti]
            [harja.palvelin.raportointi.pdf :as pdf]))

(def ^:dynamic *raportin-suoritus*
  "Tämä bindataan raporttia suoritettaessa nykyiseen raporttikomponenttiin, jotta
   kyselyitä voidaan ajaa."
  nil)

(def ^:dynamic *suorituksen-tiedot*
  "Tämä bindataan raporttia suoritettaessa raportin suoritukseen annetuilla parametreillä")

(defprotocol RaportointiMoottori
  (hae-raportit [this] "Hakee raporttien perustiedot mäppina, jossa avain on raportin nimi.")
  (hae-raportti [this raportin-nimi] "Hakee raportin suoritettavaksi")
  (suorita-raportti [this kayttaja suoritustiedot]))

(defn SQL [& haku-ja-parametrit]
  (jdbc/query (:db *raportin-suoritus*)
              haku-ja-parametrit))
                    
(defrecord Raportointi [raportit]
  component/Lifecycle
  (start [{db :db
           pdf-vienti :pdf-vienti
           :as this}]

    ;; Rekisteröidään PDF-vientipalveluun uusi käsittelijä :raportointi, joka
    ;; suorittaa raportin ja prosessoi sen XSL-FO hiccupiksi
    (pdf-vienti/rekisteroi-pdf-kasittelija!
     pdf-vienti :raportointi
     (fn [kayttaja params]
       (pdf/muodosta-pdf
        (suorita-raportti this kayttaja {:raportti (get params "raportti")}))))
    this)

  (stop [this]
    this)

  
  RaportointiMoottori
  (hae-raportit [this] @raportit)
  (hae-raportti [this nimi] (get @raportit nimi))
  (suorita-raportti [this kayttaja {raportin-nimi :raportti
                                    :as suorituksen-tiedot}]
    (println "SUORITELLAAN RAPSAA " raportin-nimi " , rapsat: " raportit)
    (when-let [suoritettava-raportti (hae-raportti this raportin-nimi)]
      (binding [*raportin-suoritus* this]
        ((:tiedot suoritettava-raportti) suorituksen-tiedot)))))


(defn luo-raportointi []
  ;; FIXME: nämä ladataan tietokannasta
  (->Raportointi (atom {:laskutusyhteenveto {:otsikko "Laskutusyhteenveto"
                                             :konteksti #{:urakka}
                                             :parametrit [{:otsikko "Hoitokausi ":nimi :hoitokausi
                                                           :tyyppi :valinta
                                                           :valinnat :valitun-urakan-hoitokaudet}
                                                          {:otsikko  "Kuukausi" :nimi :kuukausi
                                                           :tyyppi  :valinta
                                                           :valinnat :valitun-aikavalin-kuukaudet}
                                                          ]
                                             :suorita (fn [tiedot]
                                                     [:raportti
                                                      [:header "Laskutusyhteenveto " 2015]
                                                      [:taulukko
                                                       {:otsikko "Toimenpide" :nimi :toimenpide}]])}
                        :testiraportti {:otsikko "Testiraportti"
                                        :konteksti #{:urakka :koko-maa :hallintayksikko}
                                        :parametrit []
                                        :suorita (fn [tiedot]
                                                   [:raportti {:nimi "Testiraportti"
                                                               :tietoja [["Urakka" "Rymättylän päällystys"]
                                                                         ["Aika" "15.7.2015 \u2014 30.9.2015"]]}
                                                    [:otsikko "Tämä on hieno raportti"]
                                                    [:teksti "Tässäpä on sitten kappale tekstiä, joka raportissa tulee. Tämähän voisi olla mitä vain, kuten vaikka lorem ipsum dolor sit amet."]
                                                    [:taulukko [{:otsikko "Nimi" :leveys "50%"}
                                                                {:otsikko "Kappaleita" :leveys "15%"}
                                                                {:otsikko "Hinta" :leveys "15%"}
                                                                {:otsikko "Yhteensä" :leveys "20%"}]

                                                     [["Fine leather jacket" 2 199 (* 2 199)]
                                                      ["Log from blammo" 1 39 39]
                                                      ["Suristin" 10 25 250]]]

                                                    [:otsikko "Tähän taas väliotsikko"]
                                                    [:pylvaat {:otsikko "Kvartaalien luvut"}
                                                     [["Q1" 123]
                                                      ["Q2" 1500]
                                                      ["Q3" 1000]
                                                      ["Q4" 777]]]
                                                    [:yhteenveto [["PDF-generointi" "toimii"]
                                                                  ["XSL-FO" "hyvin"]]]])}

                        })))
