(ns harja.palvelin.raportointi
  "Raportointimoottorin komponentti ja apurit."
  (:require [com.stuartsierra.component :as component]
            [clojure.java.jdbc :as jdbc]
            [harja.palvelin.komponentit.pdf-vienti :as pdf-vienti]
            [harja.palvelin.raportointi.pdf :as pdf]
            [taoensso.timbre :as log]
            [harja.kyselyt.raportit :as raportit-q]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.organisaatiot :as organisaatiot-q]
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

(defn liita-suorituskontekstin-kuvaus [db {:keys [konteksti urakka-id hallintayksikko-id] :as parametrit} raportti]
  (assoc-in raportti
            [1 :tietoja]
            (as-> [["Kohde" (case konteksti
                              "urakka" "Urakka"
                              "hallintayksikko" "Hallintayksikkö"
                              "koko maa" "Koko maa")]] t
              (if (= "urakka" konteksti)
                (conj t ["Urakka" (:nimi (first (urakat-q/hae-urakka db urakka-id)))])
                t)

              (if (= "hallintayksikko" konteksti)
                (conj t ["Hallintayksikkö" (:nimi (first (organisaatiot-q/hae-organisaatio db hallintayksikko-id)))])
                t))))

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
       (let [raportti (suorita-raportti this kayttaja params)]
         (pdf/muodosta-pdf (liita-suorituskontekstin-kuvaus db params raportti)))))

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
    (log/debug "SUORITETAAN RAPORTTI " nimi)
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

