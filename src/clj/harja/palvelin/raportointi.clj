(ns harja.palvelin.raportointi
  "Raportointimoottorin komponentti ja apurit."
  (:require [com.stuartsierra.component :as component]
            [clojure.java.jdbc :as jdbc]))

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
  (start [{db :db :as this}]
    this)

  (stop [this]
    this)

  
  RaportointiMoottori
  (hae-raportit [this] @raportit)
  (hae-raportti [this nimi] (get @raportit nimi))
  (suorita-raportti [this kayttaja {raportin-nimi :raportti
                                    :as suorituksen-tiedot}]
    (let [suoritettava-raportti (hae-raportti this raportin-nimi)]
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
                                                       {:otsikko "Toimenpide" :nimi :toimenpide}]])}})))
