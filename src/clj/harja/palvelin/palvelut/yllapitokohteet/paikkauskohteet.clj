(ns harja.palvelin.palvelut.yllapitokohteet.paikkauskohteet
  (:require [com.stuartsierra.component :as component]
            [cheshire.core :as cheshire]
            [slingshot.slingshot :refer [throw+ try+]]
            [clojure.spec.alpha :as s]
            [dk.ative.docjure.spreadsheet :as xls]
            [ring.middleware.multipart-params :refer [wrap-multipart-params]]
            [harja.domain.oikeudet :as oikeudet]
            [harja.domain.roolit :as roolit]
            [harja.domain.paikkaus :as paikkaus]
            [harja.pvm :as pvm]
            [harja.kyselyt.konversio :as konv]
            [harja.geo :as geo]
            [harja.kyselyt.paikkaus :as q]
            [harja.kyselyt.urakat :as q-urakat]
            [harja.kyselyt.yllapitokohteet :as q-yllapitokohteet]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [taoensso.timbre :as log]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [harja.palvelin.palvelut.yhteyshenkilot :as yhteyshenkilot]
            [harja.palvelin.palvelut.yllapitokohteet.paikkauskohteet-excel :as p-excel]
            [harja.palvelin.komponentit.excel-vienti :as excel-vienti]))

(defn validi-pvm-vali? [validointivirheet alku loppu]
  (if (and (not (nil? alku)) (not (nil? loppu)) (.after alku loppu))
    (conj validointivirheet "Loppuaika tulee ennen alkuaikaa.")
    validointivirheet))

(defn validit-tr_osat? [validointivirheet tie alkuosa loppuosa alkuetaisyys loppuetaisyys]
  (if (and tie alkuosa alkuetaisyys loppuosa loppuetaisyys
           (>= loppuosa alkuosa))
    validointivirheet
    (conj validointivirheet "Tierekisteriosoitteessa virhe.")))

(defn- sallittu-tilamuutos? [uusi vanha rooli]
  (let [ehdotettu? #(= "ehdotettu" %)
        tilattu? #(= "tilattu" %)
        hylatty? #(= "hylatty" %)]
    (if (or (= uusi vanha) (nil? vanha))
      true
      (if (= rooli :tilaaja)
        ;; tilaaja saa tehdä seuraavat tilamuutokset.
        (or
          (and (ehdotettu? vanha) (or (tilattu? uusi) (hylatty? uusi)))
          (and (or (tilattu? vanha) (hylatty? vanha)) (ehdotettu? uusi)))
        false))))

(defn validi-paikkauskohteen-tilamuutos? [validointivirheet uusi vanha rooli]
  (if (sallittu-tilamuutos? (:paikkauskohteen-tila uusi) (:paikkauskohteen-tila vanha) rooli)
    validointivirheet
    (conj validointivirheet
          (str "Virhe tilan muutoksessa "
               (name (:paikkauskohteen-tila vanha)) " -> " (name (:paikkauskohteen-tila uusi))))))

(defn- validi-aika? [aika]
  (if (and
        (.after aika (pvm/->pvm "01.01.2000"))
        (.before aika (pvm/->pvm "01.01.2100")))
    true
    false))

(defn- validi-nimi? [nimi]
  (if (or (nil? nimi) (= "" nimi))
    false
    true))

(defn- validi-paikkauskohteen-tila? [tila]
  (boolean (some #(= tila %) ["ehdotettu" "tilattu" "hylatty" "valmis" "hyvaksytty"])))

(s/def ::nimi (s/and string? #(validi-nimi? %)))
(s/def ::alkupvm (s/and #(inst? %) #(validi-aika? %)))
(s/def ::loppupvm (s/and #(inst? %) #(validi-aika? %)))
(s/def ::paikkauskohteen-tila (s/and string? #(validi-paikkauskohteen-tila? %)))
;; TODO: Muuta tarkastamaan, että on yksi sallituista arvoista, kunhan ne päivitetään muutetaan enumeiksi.
(s/def ::tyomenetelma (s/and string? (fn [tm] (some #(= tm %) paikkaus/paikkauskohteiden-tyomenetelmat2))))
(s/def ::suunniteltu-maara (s/and number? pos?))
(s/def ::suunniteltu-hinta (s/and number? pos?))
(s/def ::yksikko paikkaus/paikkauskohteiden-yksikot)

(defn paikkauskohde-validi? [kohde vanha-kohde rooli]
  (let [validointivirheet (as-> #{} virheet
                                (if (s/valid? ::nimi (:nimi kohde))
                                  virheet
                                  (conj virheet "Paikkauskohteen nimi puuttuu."))
                                (if (s/valid? ::alkupvm (:alkupvm kohde))
                                  virheet
                                  (conj virheet "Paikkauskohteen alkupäivässä virhe."))
                                (if (s/valid? ::loppupvm (:loppupvm kohde))
                                  virheet
                                  (conj virheet "Paikkauskohteen loppupäivässä virhe."))
                                (if (s/valid? ::paikkauskohteen-tila (:paikkauskohteen-tila kohde))
                                  virheet
                                  (conj virheet "Paikkauskohteen tilassa virhe."))
                                (if (s/valid? ::tyomenetelma (:tyomenetelma kohde))
                                  virheet
                                  (conj virheet "Paikkauskohteen työmenetelmässä virhe"))
                                (if (s/valid? ::suunniteltu-hinta (:suunniteltu-hinta kohde))
                                  virheet
                                  (conj virheet "Paikkauskohteen suunnitellussa hinnassa virhe"))
                                (if (s/valid? ::suunniteltu-maara (:suunniteltu-maara kohde))
                                  virheet
                                  (conj virheet "Paikkauskohteen suunnitellussa määrässä virhe"))
                                (if (s/valid? ::yksikko (:yksikko kohde))
                                  virheet
                                  (conj virheet "Paikkauskohteen suunnitellun määrän yksikössä virhe"))
                                (if (and (s/valid? ::alkupvm (:alkupvm kohde))
                                         (s/valid? ::loppupvm (:loppupvm kohde)))
                                  (validi-pvm-vali? virheet (:alkupvm kohde) (:loppupvm kohde))
                                  virheet)
                                (validit-tr_osat? virheet (:tie kohde) (:aosa kohde) (:losa kohde) (:aet kohde) (:let kohde))
                                (validi-paikkauskohteen-tilamuutos? virheet kohde vanha-kohde rooli))]
    validointivirheet))

(defn tallenna-paikkauskohde! [db user kohde]
  (println "tallenna-paikkauskohde! voi voi-lukea? " (pr-str (oikeudet/voi-kirjoittaa? oikeudet/urakat-paikkaukset-paikkauskohteetkustannukset (:urakka-id kohde) user)) (pr-str (roolit/osapuoli user)))
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-paikkaukset-paikkauskohteetkustannukset user (:urakka-id kohde))
  (let [_ (println "tallenna-paikkauskohde! :: kohde " (pr-str (dissoc kohde :sijainti)))
        kayttajarooli (roolit/osapuoli user)
        on-kustannusoikeudet? (oikeudet/voi-kirjoittaa? oikeudet/urakat-paikkaukset-paikkauskohteetkustannukset (:urakka-id kohde) user)
        kohde-id (:id kohde)
        vanha-kohde (when kohde-id (first (q/hae-paikkauskohde db {:id kohde-id
                                                                   :urakka-id (:urakka-id kohde)})))
        ;; Tarkista pakolliset tiedot ja tietojen oikeellisuus
        validointivirheet (paikkauskohde-validi? kohde vanha-kohde kayttajarooli) ;;rooli on null?
        _ (println "tallenna-paikkauskohde! :: validointivirheet" (pr-str validointivirheet))
        ;; Jos annetulla kohteella on olemassa id, niin päivitetään. Muuten tehdään uusi
        kohde (when (empty? validointivirheet)
                (if kohde-id
                  (do
                    (q/paivita-paikkauskohde! db
                                              (merge
                                                (when on-kustannusoikeudet?
                                                  {:suunniteltu-hinta (:suunniteltu-hinta kohde)})
                                                {:id kohde-id
                                                 :ulkoinen-id (:ulkoinen-id kohde)
                                                 :nimi (:nimi kohde)
                                                 :poistettu (or (:poistettu kohde) false)
                                                 :muokkaaja-id (:id user)
                                                 :muokattu (pvm/nyt)
                                                 :yhalahetyksen-tila (:yhalahetyksen-tila kohde)
                                                 :virhe (:virhe kohde)
                                                 :tarkistettu (or (:tarkistettu kohde) nil)
                                                 :tarkistaja-id (or (:tarkistaja-id kohde) nil)
                                                 :ilmoitettu-virhe (or (:ilmoitettu-virhe kohde) nil)
                                                 :nro (:nro kohde)
                                                 :alkupvm (:alkupvm kohde)
                                                 :loppupvm (:loppupvm kohde)
                                                 :tyomenetelma (or (:tyomenetelma kohde) nil)
                                                 :tie (:tie kohde)
                                                 :aosa (:aosa kohde)
                                                 :losa (:losa kohde)
                                                 :aet (:aet kohde)
                                                 :let (:let kohde)
                                                 :ajorata (:ajorata kohde)
                                                 :paikkauskohteen-tila (:paikkauskohteen-tila kohde)
                                                 :suunniteltu-maara (:suunniteltu-maara kohde)
                                                 :yksikko (:yksikko kohde)
                                                 :lisatiedot (:lisatiedot kohde)}))
                    kohde)
                  (do
                    (println "Tallennettiin uusi :: antamalla " (pr-str kohde))
                    (q/luo-uusi-paikkauskohde<! db
                                                (merge
                                                  (when on-kustannusoikeudet?
                                                    {:suunniteltu-hinta (:suunniteltu-hinta kohde)})
                                                  {:luoja-id (:id user)
                                                   :ulkoinen-id (:ulkoinen-id kohde)
                                                   :nimi (:nimi kohde)
                                                   :urakka-id (:urakka-id kohde)
                                                   :luotu (or (:luotu kohde) (pvm/nyt))
                                                   :yhalahetyksen-tila (:yhalahetyksen-tila kohde)
                                                   :virhe (:virhe kohde)
                                                   :nro (:nro kohde)
                                                   :alkupvm (:alkupvm kohde)
                                                   :loppupvm (:loppupvm kohde)
                                                   :tyomenetelma (:tyomenetelma kohde)
                                                   :tie (:tie kohde)
                                                   :aosa (:aosa kohde)
                                                   :losa (:losa kohde)
                                                   :aet (:aet kohde)
                                                   :let (:let kohde)
                                                   :ajorata (:ajorata kohde)
                                                   :paikkauskohteen-tila (:paikkauskohteen-tila kohde)
                                                   :suunniteltu-maara (:suunniteltu-maara kohde)
                                                   :yksikko (:yksikko kohde)
                                                   :lisatiedot (:lisatiedot kohde)})))))

        _ (println "kohde: " (pr-str kohde))
        ]
    (if (empty? validointivirheet)
      kohde
      (throw+ {:type "Validaatiovirhe"
               :virheet {:koodi "ERROR" :viesti validointivirheet}}))))

(defn poista-paikkauskohde! [db user kohde]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-paikkaukset-paikkauskohteetkustannukset user (:urakka-id kohde))
  (let [_ (println "poista-paikkauskohde! :: kohde " (pr-str (dissoc kohde :sijainti)))
        id (:id kohde)
        ;; Tarkistetaan, että haluttu paikkauskohde on olemassa eikä sitä ole vielä poistettu
        poistettava (first (q/hae-paikkauskohde db {:id (:id kohde) :urakka-id (:urakka-id kohde)}))
        _ (q/poista-paikkauskohde! db id)]
    (if (empty? poistettava)
      (throw+ {:type "Error"
               :virheet [{:koodi "ERROR" :viesti "Paikkauskohdetta ei voitu poistaa, koska sitä ei löydy."}]})
      ;; Palautetaan poistettu paikkauskohde
      (assoc poistettava :poistettu true))))

(defn- kasittele-excel [db urakka-id kayttaja req]
  (let [workbook (xls/load-workbook-from-file (:path (bean (get-in req [:params "file" :tempfile]))))
        paikkauskohteet (p-excel/erottele-paikkauskohteet workbook)
        ;_ (println "Excelin data" (pr-str paikkauskohteet))
        ;; Urakalla ei saa olla kahta saman nimistä paikkauskohdetta. Niinpä varmistetaan, ettei näin ole ja jos ei ole, niin tallennetaan paikkauskohde kantaan
        kohteet (when (not (empty? paikkauskohteet))
                  (keep
                    (fn [p]
                      (let [;; Excelistä ei aseteta paikkauskohteelle tilaa, joten asetetaan se "ehdotettu" tilaan tässä
                            _ (println "rivi 219" (pr-str p))
                            p (-> p
                                  (assoc :urakka-id urakka-id)
                                  (assoc :paikkauskohteen-tila "ehdotettu"))
                            kohde (q/onko-kohde-olemassa-nimella? db (:nimi p) urakka-id)
                            ;_ (println "kohde" (pr-str kohde) (pr-str p))
                            ]
                        (if (empty? kohde)
                          ;(println "Tallennetaan kohde!")
                          (try+

                            (tallenna-paikkauskohde! db kayttaja p)

                            (catch [:type "Validaatiovirhe"] e
                              ;; TODO: Tarkista, että validaatiovirheiden ja olemassa olevien virheiden formaatti on sama
                              {:virhe (get-in e [:virheet :viesti])
                               :paikkauskohde (:nimi p)}))
                          {:virhe "Urakalta löytyy jo kohde samalla nimellä"
                           :paikkauskohde (:nimi p)})))
                    paikkauskohteet))
        tallennetut (filterv #(nil? (:virhe %)) kohteet)
        virheet (filterv #(some? (:virhe %)) kohteet)
        _ (println "kohteet" (pr-str kohteet))
        _ (println "tallennetut" tallennetut)
        _ (println "virheet" virheet)
        body (cheshire/encode (cond
                                ;; Löytyy enemmän kuin 0 tallennettua kohdetta
                                (> (count tallennetut) 0)
                                (merge {:message "OK"}
                                       (when (> (count virheet) 10000)
                                         {:virheet virheet}))
                                ;; Löytyy enemmän kuin 0 virhettä
                                (> (count virheet) 0)
                                {:virheet virheet}
                                ;; Muussa tapauksessa excelistä ei löydy paikkauskohteita
                                :else
                                {:virheet [{:virhe "Excelistä ei löydetty paikkauskohteita!"}]}))
        _ (println "Body " (pr-str body))]
    ;; Vielä ei selvää, halutaanko tallentaa mitään, jos seassa virheellisiä.
    ;; Oletetaan toistaiseksi, että halutaan tallentaa ne, joissa ei ole virheitä
    ;; ja palautetaan tieto myös virheellistä kohteista.
    (if (> (count tallennetut) 0)
      {:status 200
       :headers {"Content-Type" "application/json; charset=UTF-8"}
       :body body}
      {:status 400
       :headers {"Content-Type" "application/json; charset=UTF-8"}
       :body body}))
  )

(defn vastaanota-excel [db req]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-paikkaukset-paikkauskohteetkustannukset
                                  (:kayttaja req)
                                  (Integer/parseInt (get (:params req) "urakka-id")))
  (let [ ;_ (println "Tuli bäkkäriin !!! upload-file" (pr-str req))
        ;; TODO: Tarkista oikeudet
        urakka-id (Integer/parseInt (get (:params req) "urakka-id"))
        kayttaja (:kayttaja req)]
    ;; Tarkistetaan, että kutsussa on mukana urakka ja kayttaja
    (if (and (not (nil? urakka-id))
             (not (nil? kayttaja)))
      (kasittele-excel db urakka-id kayttaja req)
      (throw+ {:type "Error"
               :virheet [{:koodi "ERROR" :viesti "Ladatussa tiedostossa virhe."}]}))))

(defrecord Paikkauskohteet []
  component/Lifecycle
  (start [this]
    (let [http (:http-palvelin this)
          ;email (:sonja-sahkoposti this)
          db (:db this)
          excel (:excel-vienti this)]
      (julkaise-palvelu http :paikkauskohteet-urakalle
                        (fn [user tiedot]
                          (q/paikkauskohteet db user tiedot)))
      (julkaise-palvelu http :tallenna-paikkauskohde-urakalle
                        (fn [user kohde]
                          (tallenna-paikkauskohde! db user kohde)))
      (julkaise-palvelu http :laske-paikkauskohteen-pituus
                        (fn [user kohde]
                          (q/laske-paikkauskohteen-pituus db kohde)))
      (julkaise-palvelu http :poista-paikkauskohde
                        (fn [user kohde]
                          (poista-paikkauskohde! db user kohde)))
      (julkaise-palvelu http :hae-paikkauskohteen-yhteyshenkilot
                        (fn [user urakka-id]
                          (yhteyshenkilot/hae-urakan-yhteyshenkilot (:db this) user urakka-id true)))
      (julkaise-palvelu http :lue-paikkauskohteet-excelista
                        (wrap-multipart-params (fn [req] (vastaanota-excel db req)))
                        {:ring-kasittelija? true})
      (when excel
        (excel-vienti/rekisteroi-excel-kasittelija! excel :paikkauskohteet-urakalle-excel (partial #'p-excel/vie-paikkauskohteet-exceliin db)))
      this))

  (stop [this]
    (poista-palvelut
      (:http-palvelin this)
      :paikkauskohteet-urakalle
      :tallenna-paikkauskohde-urakalle
      :laske-paikkauskohteen-pituus
      :poista-paikkauskohde
      :hae-paikkauskohteen-yhteyshenkilot
      :lue-paikkauskohteet-excelista
      (when (:excel-vienti this)
        (excel-vienti/poista-excel-kasittelija! (:excel-vienti this) :paikkauskohteet-urakalle-excel)))
    this))
