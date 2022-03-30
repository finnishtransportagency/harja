(ns harja.palvelin.raportointi
  "Raportointimoottorin komponentti ja apurit."
  (:require [com.stuartsierra.component :as component]
            [clojure.java.jdbc :as jdbc]
            [clojure.string :as str]
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
            [harja.kyselyt.pohjavesialueet :as pohjavesialueet-q]
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
            [cheshire.core :as cheshire]
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

(defn- paivita-suorituksen-valmistumisaika 
  [db suoritus-id]
  (raportit-q/paivita-suorituksen-kesto<! db {:id suoritus-id}))

(defn vaadi-urakka-on-olemassa
  "Tarkistaa, että urakka löytyy Harjan tietokannasta"
  [db urakka-id]
  (when urakka-id
    (when-not (urakat-q/onko-olemassa? db urakka-id)
      (throw (SecurityException. (str "Urakkaa " urakka-id " ei ole olemassa."))))))

(defn vaadi-hallintayksikko-on-olemassa
  "Tarkistaa, että hallintayksikko-id löytyy Harjan tietokannasta"
  [db hallintayksikko-id]
  (when hallintayksikko-id
    (when-not (:exists (first (organisaatiot-q/onko-olemassa db hallintayksikko-id)))
      (throw (SecurityException. (str "Hallintayksikköä " hallintayksikko-id " ei ole olemassa."))))))

(defn parsi-urakka-tai-organisaatioroolit
  "Parsii ne roolit, joilla on urakka- tai organisaatiolinkki, eli urakka- ja organisaatioroolit. Jäljelle pilkulla eroteltu merkkijono jossa kukin rooli vain yhteen kertaan."
  [roolit]
  (when-not (empty? roolit)
    (str/join ","
              (apply
                clojure.set/union
                (map (fn [[k v]]
                       v)
                     roolit)))))

(defn parsi-roolit
  "Parsii ns. yleiset roolit, joilla ei ole urakka- tai organisaatiolinkkiä"
  [roolit]
  (when-not (empty? roolit)
    (str/join "," roolit)))

(defn luo-suoritustieto-raportille
  [db user tiedot]
  (let [{:keys [urakka-id nimi konteksti kasittelija parametrit hallintayksikko-id]} tiedot
        {:keys [alkupvm loppupvm]} parametrit
        {{kayttajan-organisaatio :id} :organisaatio
         :keys [roolit urakkaroolit organisaatioroolit]} user
        _ (vaadi-urakka-on-olemassa db urakka-id)
        _ (vaadi-hallintayksikko-on-olemassa db hallintayksikko-id)
        tiedot {:urakka_id urakka-id
                :suorittajan_organisaatio kayttajan-organisaatio
                :aikavali_alkupvm alkupvm
                :aikavali_loppupvm loppupvm
                :hallintayksikko_id hallintayksikko-id
                :konteksti konteksti
                :raportti (name nimi)
                :rooli (parsi-roolit roolit)
                :urakkarooli (parsi-urakka-tai-organisaatioroolit urakkaroolit)
                :organisaatiorooli (parsi-urakka-tai-organisaatioroolit organisaatioroolit)
                :suoritustyyppi (if (keyword? kasittelija) ;; voi olla :pdf tai :excel, muussa tapauksessa selaimessa tehty
                                  (name kasittelija)
                                  "selain")
                :parametrit (cheshire/encode parametrit)}
        {:keys [id]} (raportit-q/luo-suoritustieto<! db tiedot)]
    id))

(defn liita-suorituskontekstin-kuvaus [db {:keys [konteksti urakka-id urakoiden-nimet
                                                  hallintayksikko-id parametrit]
                                           :as kaikki-parametrit} raportti]
  (let [urakka (when urakka-id
                 (first (urakat-q/hae-urakka db urakka-id)))
        hy-nimi (when hallintayksikko-id
                  (-> (organisaatiot-q/hae-organisaatio db hallintayksikko-id)
                      first
                      :nimi))]
    (->
      raportti
      (assoc-in
        [1 :raportin-yleiset-tiedot]
        {:urakka (case konteksti
                   "urakka" (:nimi urakka)

                   "monta-urakkaa" (str/join ", " urakoiden-nimet)

                   "hallintayksikko" hy-nimi

                   "koko maa" "Koko maa")
         :alkupvm (some-> parametrit :alkupvm pvm/pvm)
         :loppupvm (some-> parametrit :loppupvm pvm/pvm)
         :raportin-nimi (get-in raportti [1 :nimi])})
      (assoc-in
        [1 :tietoja]
        (as-> [["Kohde" (case konteksti
                          "urakka" "Urakka"
                          "monta-urakkaa" (if (> (count urakoiden-nimet) 1)
                                            "Monta urakkaa"
                                            "Urakka")
                          "hallintayksikko" "Hallintayksikkö"
                          "koko maa" "Koko maa")]] t
              (if (= "urakka" konteksti)
                (concat t [["Urakka" (:nimi urakka)]
                           ["Urakoitsija" (:urakoitsija_nimi urakka)]])
                t)

              (if (= "monta-urakkaa" konteksti)
                (concat t [[(if (> (count urakoiden-nimet) 1)
                              "Urakat"
                              "Urakka")
                            (clojure.string/join ", " urakoiden-nimet)]])
                t)

              (if (= "hallintayksikko" konteksti)
                (concat t [["Hallintayksikkö" hy-nimi]
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
                t))))))

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

(defn paivita_raportti_toteutuneet_materiaalit! [db]
  (ajastettu-tehtava/ajasta-paivittain [5 0 0]
                                       (fn [_]
                                         (lukot/yrita-ajaa-lukon-kanssa
                                           db "paivita_raportti_toteutuneet_materiaalit"
                                           #(do
                                              (log/info "ajasta-paivittain :: paivita_raportti_toteutuneet_materiaalit :: Alkaa " (pvm/nyt))
                                              (paivita-kaynnissolevien-hoitourakoiden-materiaalicachet-eiliselta db)
                                              (raportit-q/paivita_raportti_toteutuneet_materiaalit db)
                                              (log/info "paivita_raportti_toteutuneet_materiaalit :: Loppuu " (pvm/nyt)))
                                           ;; otetaan 3h lukko, jotta varmasti voimassa ajon jälkeen
                                           (* 60 180)))))

(defn paivita_raportti_pohjavesialueiden_suolatoteumat! [db]
  (ajastettu-tehtava/ajasta-paivittain [3 30 0]
                                       (fn [_]
                                         (lukot/yrita-ajaa-lukon-kanssa
                                           db "paivita_raportti_pohjavesialueiden_suolatoteumat"
                                           #(do
                                              (log/info "ajasta-paivittain :: paivita_pohjavesialue_kooste  :: Alkaa " (pvm/nyt))
                                              (pohjavesialueet-q/paivita-pohjavesialue-kooste db)
                                              (log/info "paivita_pohjavesialue_kooste :: Loppuu " (pvm/nyt))
                                              (log/info "ajasta-paivittain :: paivita_raportti_pohjavesialueiden_suolatoteumat :: Alkaa " (pvm/nyt))
                                              (raportit-q/paivita_raportti_pohjavesialueiden_suolatoteumat db)
                                              (log/info "paivita_raportti_pohjavesialueiden_suolatoteumat :: Loppuu " (pvm/nyt)))
                                           ;; otetaan 3h lukko, jotta varmasti voimassa ajon jälkeen
                                           (* 60 180)))))

(defn paivita_raportti_toteuma_maarat! [db]
  (ajastettu-tehtava/ajasta-paivittain [0 1 0]
                                       (fn [_]
                                         (lukot/yrita-ajaa-lukon-kanssa
                                           db "paivita_raportti_toteuma_maarat"
                                           #(do
                                              (log/info "ajasta-paivittain :: paivita_raportti_toteuma_maarat :: Alkaa " (pvm/nyt))
                                              (raportit-q/paivita_raportti_toteuma_maarat db)
                                              (log/info "paivita_raportti_toteuma_maarat :: Loppuu " (pvm/nyt)))
                                           ;; otetaan 3h lukko, jotta varmasti voimassa ajon jälkeen
                                           (* 60 180)))))

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
       (let [raportti (suorita-raportti this kayttaja (assoc params :kasittelija :pdf))]
         (if (= :raportoinnissa-ruuhkaa raportti)
           (raportoinnissa-ruuhkaa-sivu "pdf" params)
           (pdf/muodosta-pdf (liita-suorituskontekstin-kuvaus db params raportti))))))

    (when excel-vienti
      (excel-vienti/rekisteroi-excel-kasittelija!
       excel-vienti :raportointi
       (fn [workbook kayttaja params]
         (let [raportti (suorita-raportti this kayttaja (assoc params :kasittelija :excel))]
           (if (= :raportoinnissa-ruuhkaa raportti)
             (raportoinnissa-ruuhkaa-sivu "excel" params)
             (do (log/info "RAPORTTI MUODOSTETTU, TEHDÄÄN EXCEL " workbook)
                 (excel/muodosta-excel (liita-suorituskontekstin-kuvaus db params raportti)
                                       workbook)))))))
    ;; Aloita materiaalicachepäivitysten ajastettutehtävä
    (assoc this :toteutuneet-materiaalit-ajastus (paivita_raportti_toteutuneet_materiaalit! db)
                :pohjavesialueiden-suolatoteumat-ajastus (paivita_raportti_pohjavesialueiden_suolatoteumat! db)
                :toteuma-maarat-ajastus (paivita_raportti_toteuma_maarat! db)))

  (stop [this]
    (let [toteutuneet-materiaalit-ajastus (:toteutuneet-materiaalit-ajastus this)
          pohjavesialueiden-suolatoteumat-ajastus (:pohjavesialueiden-suolatoteumat-ajastus this)
          toteuma-maarat-ajastus (:toteuma-maarat-ajastus this)]
      (toteutuneet-materiaalit-ajastus)
      (pohjavesialueiden-suolatoteumat-ajastus)
      (toteuma-maarat-ajastus))
    (reset! ajossa-olevien-raporttien-lkm 0)
    this)

  RaportointiMoottori
  (hae-raportit [this] raportit)

  (hae-raportti [this nimi] (get (hae-raportit this) nimi))
  (suorita-raportti [{db :db
                      db-replica :db-replica
                      :as this} kayttaja {:keys [nimi konteksti kasittelija parametrit]
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
          (binding [*raportin-suoritus* this]
            ;; Tallennetaan loki raportin ajon startista
            (let [parametrit (assoc parametrit :kasittelija kasittelija)
                  _ (log/debug "SUORITETAAN RAPORTTI " nimi " kontekstissa " konteksti
                     " parametreilla " parametrit)
                  suoritus-id (luo-suoritustieto-raportille
                               db 
                               kayttaja 
                               (assoc suorituksen-tiedot :parametrit parametrit :suoritettava suoritettava-raportti))
                  raportti ((:suorita suoritettava-raportti)
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
                              "koko maa" parametrit))]
              ;; tallennetaan suorituksen lopetusaika
              (paivita-suorituksen-valmistumisaika db suoritus-id)
              raportti)))))))


(defn luo-raportointi []
  (->Raportointi raportit-nimen-mukaan (atom 0)))
