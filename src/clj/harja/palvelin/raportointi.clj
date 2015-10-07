(ns harja.palvelin.raportointi
  "Raportointimoottorin komponentti ja apurit."
  (:require [com.stuartsierra.component :as component]
            [clojure.java.jdbc :as jdbc]
            [harja.palvelin.komponentit.pdf-vienti :as pdf-vienti]
            [harja.palvelin.raportointi.pdf :as pdf]
            [taoensso.timbre :as log]
            [harja.kyselyt.raportit :as raportit-q]
            ;; vaaditaan built in raportit
            [harja.palvelin.raportointi.raportit.laskutusyhteenveto]
            [harja.palvelin.raportointi.raportit.materiaali]
            [harja.palvelin.raportointi.raportit.yksikkohintaiset-tyot]))

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
       (let [rapos (suorita-raportti this kayttaja params)]
         (log/debug "SUORITETTU RAPSA: " (pr-str rapos))
         (pdf/muodosta-pdf
          rapos))))

    this)

  (stop [this]
    this)

  
  RaportointiMoottori
  (hae-raportit [this]
    (or @raportit
        (try
          (let [r (raportit-q/raportit (:db this))]
            (reset! raportit r)
            r)
          (catch Exception e
            (log/warn e "Raporttien hakemisessa virhe!")
            {}))))

  (hae-raportti [this nimi] (get (hae-raportit this) nimi))
  (suorita-raportti [{db :db :as this} kayttaja {:keys [nimi konteksti parametrit] :as suorituksen-tiedot}]
    (log/debug "SUORITELLAAN RAPSAA " nimi " , rapsat: " raportit)
    (when-let [suoritettava-raportti (hae-raportti this nimi)]
      (binding [*raportin-suoritus* this]
        ((:suorita suoritettava-raportti) db kayttaja
         (condp = konteksti
           "urakka" (assoc parametrit
                           :urakka-id (:urakka-id suorituksen-tiedot))
           "hallintayksikko" (assoc parametrit
                                    :hallintayksikko-id (:hallintayksikko-id suorituksen-tiedot))
           "koko maa" parametrit))))))


(defn luo-raportointi []
  (->Raportointi (atom nil)))

#_{:laskutusyhteenveto {:otsikko "Laskutusyhteenveto"
                        :konteksti #{:urakka}
                        :parametrit [{:otsikko "Hoitokausi ":nimi :hoitokausi
                                      :tyyppi :valinta
                                      :valinnat :valitun-urakan-hoitokaudet}
                                     {:otsikko  "Kuukausi" :nimi :kuukausi
                                      :tyyppi  :valinta
                                      :valinnat :valitun-aikavalin-kuukaudet}
                                     ]
                        :suorita #'harja.palvelin.raportointi.raportit.laskutusyhteenveto/suorita}
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

   }
