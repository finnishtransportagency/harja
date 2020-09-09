(ns harja.palvelin.raportointi
  "Raportointimoottorin komponentti ja apurit."
  (:require [com.stuartsierra.component :as component]
            [clojure.java.jdbc :as jdbc]
            [harja.palvelin.komponentit.pdf-vienti :as pdf-vienti]
            [harja.palvelin.komponentit.excel-vienti :as excel-vienti]
            [harja.palvelin.raportointi.pdf :as pdf]
            [harja.palvelin.raportointi.excel :as excel]
            [harja.palvelin.tyokalut.ajastettu-tehtava :as ajastettu-tehtava]
            [taoensso.timbre :as log]
            [harja.kyselyt
             [urakat :as urakat-q]
             [sopimukset :as sopimukset-q]
             [organisaatiot :as organisaatiot-q]
             [materiaalit :as materiaalit]
             [raportit :as raportit-q]]
            [harja.palvelin.raportointi.raportit :refer [raportit-nimen-mukaan]]
            [harja.domain.oikeudet :as oikeudet]
            [harja.domain.raportointi :as raportti-domain]
            [harja.domain.roolit :as roolit]
            [harja.pvm :as pvm]
            [new-reliquary.core :as nr]
            [hiccup.core :refer [html]]
            [harja.transit :as t]
            [slingshot.slingshot :refer [throw+]]
            [clojure.java.io :as io]
            [harja.fmt :as fmt]
            [harja.palvelin.tyokalut.lukot :as lukot]))

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

(defn liita-suorituskontekstin-kuvaus [db {:keys [konteksti urakka-id urakoiden-nimet
                                                  hallintayksikko-id parametrit]
                                           :as kaikki-parametrit} raportti]
  (assoc-in raportti
            [1 :tietoja]
            (as-> [["Kohde" (case konteksti
                              "urakka" "Urakka"
                              "monta-urakkaa" (if (> (count urakoiden-nimet) 1)
                                                "Monta urakkaa"
                                                "Urakka")
                              "hallintayksikko" "Hallintayksikkö"
                              "koko maa" "Koko maa")]] t
              (if (= "urakka" konteksti)
                (let [ur (first (urakat-q/hae-urakka db urakka-id))]
                  (concat t [["Urakka" (:nimi ur)]
                             ["Urakoitsija" (:urakoitsija_nimi ur)]]))

                t)

              (if (= "monta-urakkaa" konteksti)
                (concat t [[(if (> (count urakoiden-nimet) 1)
                              "Urakat"
                              "Urakka")
                            (clojure.string/join ", " urakoiden-nimet)]])
                t)

              (if (= "hallintayksikko" konteksti)
                  (concat t [["Hallintayksikkö"
                             (:nimi (first (organisaatiot-q/hae-organisaatio db
                                                                             hallintayksikko-id)))]
                             (if (and (:urakkatyyppi parametrit)
                                      ;; Vesiväylä- ja kanavaurakoiden osalta urakkatyyppien käsittely monimutkaisempaa eikä siksi tehty tässä
                                      (#{:hoito :paallystys :valaistus :tiemerkinta :paikkaus} (:urakkatyyppi parametrit)))
                               [(str "Tyypin " (fmt/urakkatyyppi-fmt (:urakkatyyppi parametrit)) " urakoita käynnissä")
                                (count (urakat-q/hae-hallintayksikon-kaynnissa-olevat-urakkatyypin-urakat
                                         db {:hal hallintayksikko-id
                                             :urakkatyyppi (name (:urakkatyyppi parametrit))}))]
                               ["Urakoita käynnissä"
                             (count (urakat-q/hae-hallintayksikon-kaynnissa-olevat-urakat
                                      db hallintayksikko-id))])])
                t)

              (if (= "koko maa" konteksti)
                (if (and (:urakkatyyppi parametrit)
                         ;; Vesiväylä- ja kanavaurakoiden osalta urakkatyyppien käsittely monimutkaisempaa eikä siksi tehty tässä
                         (#{:hoito :paallystys :valaistus :tiemerkinta :paikkaus} (:urakkatyyppi parametrit)))
                  (conj t [(str "Tyypin " (fmt/urakkatyyppi-fmt (:urakkatyyppi parametrit)) " urakoita käynnissä")
                           (count (urakat-q/hae-kaynnissa-olevat-urakkatyypin-urakat db
                                                                                     {:urakkatyyppi (name (:urakkatyyppi parametrit))}))])
                  (conj t ["Urakoita käynnissä" (count (urakat-q/hae-kaynnissa-olevat-urakat db))]))
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

(defn- paivita-urakan-materiaalin-kayton-cachet-eiliselta [db urakka-id]
  (let [urakan-sopimus-idt (map :id
                                (sopimukset-q/hae-urakan-sopimus-idt db
                                                                     {:urakka_id urakka-id}))]
    (doseq [sopimus-id urakan-sopimus-idt]
      ;; Päivitetään sopimuksen_kaytetty_materiaali (sisältää saman datan kuin myöhemmin luotu materialized view raportti_toteutuneet_materiaalit, jos aika sallii, voidaan refaktoroida ja hankkiutua toisesta eroon)
      ;; (Ympäristöraporttia varten)
      (materiaalit/paivita-sopimuksen-materiaalin-kaytto db {:sopimus sopimus-id
                                                             :alkupvm (pvm/eilinen)}))
    ;; Päivitetään taulu urakan_materiaalin_kaytto_hoitoluokittain (Ympäristöraporttia varten)
    (materiaalit/paivita-urakan-materiaalin-kaytto-hoitoluokittain db {:urakka urakka-id
                                                                       :alkupvm (pvm/eilinen)
                                                                       :loppupvm (pvm/eilinen)})))

(defn- paivita-kaynnissolevien-hoitourakoiden-materiaalicachet-eiliselta [db]
  (let [urakka-idt (mapv :id
                         (urakat-q/hae-kaynnissa-olevat-hoitourakat db))]
    (jdbc/with-db-transaction [db db]
                              (doseq [u urakka-idt]
                                (paivita-urakan-materiaalin-kayton-cachet-eiliselta db u)))
    :paivitetty))

;; Asetetaan raportticachen päivitys klo 7:15, koska tietty urakoitsija lähettää usein jopa 7h pitkiä toteumia.
;; Esim. t.alkanut klo 22, saapuu API:in klo 5. Näin saadaan ajettua nekin vielä tuoreeltaan raporteille
(defn paivita-raportti-cache-oisin! [db]
  (ajastettu-tehtava/ajasta-paivittain [1 0 0]
                                       (lukot/aja-lukon-kanssa
                                         db "paivita-raportti-cache-oisin!"
                                         #(do
                                              (log/info "paivita-raportti-cache-oisin! :: Alkaa " (pvm/nyt))
                                              (paivita-kaynnissolevien-hoitourakoiden-materiaalicachet-eiliselta db)
                                              (raportit-q/paivita_raportti_cachet db)
                                              (log/info "paivita-raportti-cache-oisin! :: loppuu " (pvm/nyt))))))

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
    ;; Aloita materiaalicachepäivitysten ajastettutehtävä
    (assoc this :raportti-cache-ajastus (paivita-raportti-cache-oisin! db)))

  (stop [this]
    ((:raportti-cache-ajastus this))
    this)

  RaportointiMoottori
  (hae-raportit [this] raportit)

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
               "monta-urakkaa" (assoc parametrit
                                 :urakoiden-nimet (:urakoiden-nimet suorituksen-tiedot))
               "hallintayksikko" (assoc parametrit
                                        :hallintayksikko-id
                                        (:hallintayksikko-id suorituksen-tiedot))
               "koko maa" parametrit))))))))


(defn luo-raportointi []
  (->Raportointi raportit-nimen-mukaan (atom 0)))
