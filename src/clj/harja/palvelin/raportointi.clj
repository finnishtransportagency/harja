(ns harja.palvelin.raportointi
  "Raportointimoottorin komponentti ja apurit."
  (:require [com.stuartsierra.component :as component]
            [clojure.java.jdbc :as jdbc]
            [harja.palvelin.komponentit.pdf-vienti :as pdf-vienti]
            [harja.palvelin.komponentit.excel-vienti :as excel-vienti]
            [harja.palvelin.raportointi.pdf :as pdf]
            [harja.palvelin.raportointi.excel :as excel]
            [taoensso.timbre :as log]
            [harja.kyselyt.raportit :as raportit-q]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.organisaatiot :as organisaatiot-q]
            ;; vaaditaan built in raportit
            [harja.palvelin.raportointi.raportit.erilliskustannukset]
            [harja.palvelin.raportointi.raportit.ilmoitus]
            [harja.palvelin.raportointi.raportit.laskutusyhteenveto]
            [harja.palvelin.raportointi.raportit.materiaali]
            [harja.palvelin.raportointi.raportit.muutos-ja-lisatyot]
            [harja.palvelin.raportointi.raportit.yksikkohintaiset-tyot-paivittain]
            [harja.palvelin.raportointi.raportit.yksikkohintaiset-tyot-tehtavittain]
            [harja.palvelin.raportointi.raportit.yksikkohintaiset-tyot-kuukausittain]
            [harja.palvelin.raportointi.raportit.suolasakko]
            [harja.palvelin.raportointi.raportit.tiestotarkastus]
            [harja.palvelin.raportointi.raportit.kelitarkastus]
            [harja.palvelin.raportointi.raportit.laaduntarkastus]
            [harja.palvelin.raportointi.raportit.laatupoikkeama]
            [harja.palvelin.raportointi.raportit.siltatarkastus]
            [harja.palvelin.raportointi.raportit.sanktio]
            [harja.palvelin.raportointi.raportit.sanktioraportti-yllapito]
            [harja.palvelin.raportointi.raportit.soratietarkastus]
            [harja.palvelin.raportointi.raportit.valitavoiteraportti]
            [harja.palvelin.raportointi.raportit.ymparisto]
            [harja.palvelin.raportointi.raportit.tyomaakokous]
            [harja.palvelin.raportointi.raportit.turvallisuuspoikkeamat]
            [harja.palvelin.raportointi.raportit.toimenpideajat]
            [harja.palvelin.raportointi.raportit.toimenpidepaivat]
            [harja.palvelin.raportointi.raportit.toimenpidekilometrit]
            [harja.palvelin.raportointi.raportit.indeksitarkistus]
            [harja.domain.oikeudet :as oikeudet]
            [harja.domain.raportointi :as raportti-domain]
            [harja.domain.roolit :as roolit]
            [new-reliquary.core :as nr]
            [hiccup.core :refer [html]]
            [harja.transit :as t]
            [slingshot.slingshot :refer [throw+]]))

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

(def tarvitsee-write-tietokannan #{:laskutusyhteenveto :indeksitarkistus :tyomaakokous})

(defn liita-suorituskontekstin-kuvaus [db {:keys [konteksti urakka-id hallintayksikko-id]
                                           :as parametrit} raportti]
  (assoc-in raportti
            [1 :tietoja]
            (as-> [["Kohde" (case konteksti
                              "urakka" "Urakka"
                              "hallintayksikko" "Hallintayksikkö"
                              "koko maa" "Koko maa")]] t
              (if (= "urakka" konteksti)
                (let [ur (first (urakat-q/hae-urakka db urakka-id))]
                  (concat t [["Urakka" (:nimi ur)]
                             ["Urakoitsija" (:urakoitsija_nimi ur)]]))

                t)

              (if (= "hallintayksikko" konteksti)
                (concat t [["Hallintayksikkö"
                            (:nimi (first (organisaatiot-q/hae-organisaatio db
                                                                            hallintayksikko-id)))]
                           ["Urakoita käynnissä"
                            (count (urakat-q/hae-hallintayksikon-kaynnissa-olevat-urakat
                                    db hallintayksikko-id))]])
                t)

              (if (= "koko maa" konteksti)
                (conj t ["Urakoita käynnissä" (count (urakat-q/hae-kaynnissa-olevat-urakat db))])
                t))))

(defmacro max-n-samaan-aikaan [n lkm-atomi tulos-jos-ruuhkaa & body]
  `(let [n# ~n
         lkm# ~lkm-atomi]
     (if (>= @lkm# n#)
       ~tulos-jos-ruuhkaa
       (try
         (swap! lkm# inc)
         ~@body
         (finally
           (swap! lkm# dec))))))

(defn raportoinnissa-ruuhkaa-sivu [polku params]
  {:status 200
   :headers {"Content-Type" "text/html; charset=UTF-8"}
   :body (html
          [:html
           [:head
            [:title "Raportoinnissa ruuhkaa"]
            [:script {:type "text/javascript"}
             "setTimeout(function() { window.location = '"
             (str polku "?_=raportointi&parametrit="
                  (java.net.URLEncoder/encode (t/clj->transit params)))
             "'; }, 5000);"]]
           [:body
            [:div "Raportoinnissa on nyt ruuhkaa. Yritetään kohta uudelleen. "
             "Sivu latautuu automaattisesti uudelleen hetken kuluttua."]]])})

(defrecord Raportointi [raportit ajossa-olevien-raporttien-lkm]
  component/Lifecycle
  (start [{db :db
           pdf-vienti :pdf-vienti
           excel-vienti :excel-vienti
           :as this}]

    ;; Rekisteröidään PDF-vientipalveluun uusi käsittelijä :raportointi, joka
    ;; suorittaa raportin ja prosessoi sen XSL-FO hiccupiksi
    (pdf-vienti/rekisteroi-pdf-kasittelija!
     pdf-vienti :raportointi
     (fn [kayttaja params]
       (let [raportti (suorita-raportti this kayttaja params)]
         (if (= :raportoinnissa-ruuhkaa raportti)
           (raportoinnissa-ruuhkaa-sivu "pdf" params)
           (pdf/muodosta-pdf (liita-suorituskontekstin-kuvaus db params raportti))))))

    (when excel-vienti
      (excel-vienti/rekisteroi-excel-kasittelija!
       excel-vienti :raportointi
       (fn [workbook kayttaja params]
         (let [raportti (suorita-raportti this kayttaja params)]
           (if (= :raportoinnissa-ruuhkaa raportti)
             (raportoinnissa-ruuhkaa-sivu "excel" params)
             (do (log/info "RAPORTTI MUODOSTETTU, TEHDÄÄN EXCEL " workbook)
                 (excel/muodosta-excel (liita-suorituskontekstin-kuvaus db params raportti)
                                       workbook)))))))
    this)

  (stop [this]
    this)

  RaportointiMoottori
  (hae-raportit [this]
    (or @raportit
        (try
          (let [r (raportit-q/raportit (:db this))]
            (log/debug "Raportit saatu: " (pr-str r))
            (reset! raportit r)
            r)
          (catch Exception e
            (log/warn e "Raporttien hakemisessa virhe!")
            {}))))

  (hae-raportti [this nimi] (get (hae-raportit this) nimi))
  (suorita-raportti [{db :db
                      db-replica :db-replica
                      :as this} kayttaja {:keys [nimi konteksti parametrit]
                                          :as suorituksen-tiedot}]
    (max-n-samaan-aikaan
     5 ajossa-olevien-raporttien-lkm :raportoinnissa-ruuhkaa
     (nr/with-newrelic-transaction
       "Raportin suoritus"
       (str nimi)
       #(when-let [suoritettava-raportti (hae-raportti this nimi)]
         (when-not (= "urakka" konteksti)
           (when-not (raportti-domain/voi-nahda-laajemman-kontekstin-raportit? kayttaja)
             (throw+ (roolit/->EiOikeutta (str "Käyttäjällä " (:kayttajanimi kayttaja) " ei ole oikeutta laajennetun kontekstin urakoihin")))))
         (oikeudet/vaadi-lukuoikeus (oikeudet/raporttioikeudet (:kuvaus suoritettava-raportti))
                                     kayttaja (when (= "urakka" konteksti)
                                                (:urakka-id suorituksen-tiedot)))
          (log/debug "SUORITETAAN RAPORTTI " nimi " kontekstissa " konteksti
                     " parametreilla " parametrit)
          (binding [*raportin-suoritus* this]
            ((:suorita suoritettava-raportti)
             (if (or (nil? db-replica)
                     (tarvitsee-write-tietokannan nimi))
               db
               db-replica)
             kayttaja
             (condp = konteksti
               "urakka" (assoc parametrit
                               :urakka-id (:urakka-id suorituksen-tiedot))
               "hallintayksikko" (assoc parametrit
                                        :hallintayksikko-id
                                        (:hallintayksikko-id suorituksen-tiedot))
               "koko maa" parametrit))))))))


(defn luo-raportointi []
  (->Raportointi (atom nil) (atom 0)))
