(ns harja.palvelin.palvelut.yllapitokohteet.paikkauskohteet
  (:require [com.stuartsierra.component :as component]
            [cheshire.core :as cheshire]
            [hiccup.core :refer [html]]
            [slingshot.slingshot :refer [throw+ try+]]
            [clojure.spec.alpha :as s]
            [taoensso.timbre :as log]
            [clojure.string :as str]
            [clojure.set :as set]
            [dk.ative.docjure.spreadsheet :as xls]
            [ring.middleware.multipart-params :refer [wrap-multipart-params]]
            [specql.core :refer [fetch update! insert! upsert! delete!]]
            [harja.domain.oikeudet :as oikeudet]
            [harja.domain.roolit :as roolit]
            [harja.domain.paikkaus :as paikkaus]
            [harja.domain.muokkaustiedot :as muokkaustiedot]
            [harja.pvm :as pvm]
            [harja.fmt :as fmt]
            [harja.kyselyt.paikkaus :as paikkaus-q]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.palvelin.komponentit.fim :as fim]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.palvelin.integraatiot.sahkoposti :as sahkoposti]
            [harja.tyokalut.html :as html-tyokalut]
            [harja.palvelin.palvelut.yhteyshenkilot :as yhteyshenkilot]
            [harja.palvelin.palvelut.yllapitokohteet.paikkauskohteet-excel :as p-excel]
            [harja.palvelin.komponentit.excel-vienti :as excel-vienti]
            [specql.core :as specql]))

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
        hylatty? #(= "hylatty" %)
        valmis? #(= "valmis" %)]
    ;; Kaikille sallitut tilamuutokset, eli ei muutosta tai uusi paikkauskohde.
    (if (or (= uusi vanha)
            (nil? vanha))
      true
      (cond
        ;; Tilaajan sallitut tilamuutokset
        (= rooli :tilaaja) (or
                             (and (ehdotettu? vanha) (or (tilattu? uusi) (hylatty? uusi)))
                             (and (or (tilattu? vanha) (hylatty? vanha)) (ehdotettu? uusi))
                             (and (tilattu? vanha) (valmis? uusi)))
        ;; Urakoitsija saa merkata tilatun valmiiksi
        (= rooli :urakoitsija) (and (tilattu? vanha) (valmis? uusi))
        :default
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
(s/def ::tyomenetelma (s/and string? (fn [tm] (some #(= tm %) paikkaus/paikkauskohteiden-tyomenetelmat))))
(s/def ::suunniteltu-maara (s/and number? pos?))
(s/def ::suunniteltu-hinta (s/and number? pos?))
(s/def ::ulkoinen-id (s/and number? pos?))
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
                                (if (s/valid? ::ulkoinen-id (:ulkoinen-id kohde))
                                  virheet
                                  (conj virheet "Paikkauskohteen ulkoinen-id puuttuu"))
                                (if (and (s/valid? ::alkupvm (:alkupvm kohde))
                                         (s/valid? ::loppupvm (:loppupvm kohde)))
                                  (validi-pvm-vali? virheet (:alkupvm kohde) (:loppupvm kohde))
                                  virheet)
                                (validit-tr_osat? virheet (:tie kohde) (:aosa kohde) (:losa kohde) (:aet kohde) (:let kohde))
                                (validi-paikkauskohteen-tilamuutos? virheet kohde vanha-kohde rooli))]
    validointivirheet))

(defn- laheta-sahkoposti [fim email sampo-id roolit viestin-otsikko viestin-vartalo]
  (let [vastaanottajat (fim/hae-urakan-kayttajat-jotka-roolissa fim sampo-id roolit)
        _ (log/debug "laheta-sahkoposti :: vastaanottajat" (pr-str vastaanottajat))]
    (try
      ;; Lähetä sähköposti käyttäjäroolin perusteella
      (doseq [henkilo vastaanottajat]
        (do
          (sahkoposti/laheta-viesti!
            email
            (sahkoposti/vastausosoite email)
            (:sahkoposti henkilo)
            (str "Harja: " viestin-otsikko)
            viestin-vartalo)
          (log/debug "Sähköposti lähtetty roolin perusteella: " (pr-str (:sahkoposti henkilo)) " - " (pr-str viestin-otsikko))))

      (catch Exception e
        (log/error (format "Sähköpostin lähetys vastaanottajalle epäonnistui. Virhe: %s" (pr-str e)))))))

(defn hae-paikkauskohteen-tiemerkintaurakat [db user tiedot]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-paikkaukset-paikkauskohteetkustannukset user (:urakka-id tiedot))
  (let [hallintayksikot (urakat-q/urakan-hallintayksikko db {:id (:urakka-id tiedot)})
        urakan-hallintayksikko-id (:hallintayksikko-id (first hallintayksikot))
        urakat (urakat-q/hae-urakat-tyypilla-ja-hallintayksikolla
                 db {:hallintayksikko-id urakan-hallintayksikko-id
                     :urakkatyyppi "tiemerkinta"})]
    urakat))

(defn ilmoita-tiemerkintaan [db fim email user tiedot]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-paikkaukset-paikkauskohteetkustannukset user (:urakka-id tiedot))
  (let [;; Haetaan tiemerkintäurakan sampo-id sähköpostin lähetystä varten
        urakka-sampo-id (urakat-q/hae-urakan-sampo-id db (:tiemerkinta-urakka tiedot))
        pituus (paikkaus-q/laske-paikkauskohteen-pituus db {:tie (:tie tiedot)
                                                            :aosa (:aosa tiedot)
                                                            :aet (:aet tiedot)
                                                            :losa (:losa tiedot)
                                                            :let (:let tiedot)})
        otsikko (str "Kohteen " (:paikkauskohde-nimi tiedot) " paikkaustyö on valmistunut")
        viesti (html
                 [:div
                  [:p otsikko]
                  (html-tyokalut/tietoja [["Kohde: " (:paikkauskohde-nimi tiedot)]
                                          ["Sijainti: " (str (:tie tiedot) " " (:aosa tiedot) "/" (:aet tiedot) " - " (:losa tiedot) "/" (:let tiedot))]
                                          ["Pituus: " (str (:pituus pituus) " m")]
                                          ["Valmis tiemerkintään: " (if (:valmistumispvm tiedot)
                                                                      (fmt/pvm (:valmistumispvm tiedot))
                                                                      "Ei tiedossa")]
                                          ["Viesti paikkaajalta: " (:viesti tiedot)]])
                  [:p "Tämä on automaattisesti luotu viesti HARJA-järjestelmästä. Älä vastaa tähän viestiin."]])
        _ (log/debug "laheta-viesti-tiemerkintaan :: viesti" (pr-str viesti))]

    (laheta-sahkoposti fim email urakka-sampo-id
                       #{"urakan vastuuhenkilö"}
                       otsikko
                       viesti)
    (when (:kopio-itselle? tiedot)
      (laheta-sahkoposti fim email urakka-sampo-id
                         (:sahkoposti user)
                         otsikko
                         viesti))
    tiedot))

(defn tarkista-tilamuutoksen-vaikutukset
  "Kun paikkauskohteen tila muuttuu, niin on mahdollisuus urakoitsijalle tai tilaajalle lähetetään sähköpostia.
  Esimerkiksi tilan vaihtuessa:
  1. Ehdotettu -> Tilattu, lähetetään urakoitsijalle sähköpostitse ilmoitus tilauksesta.
  2. Tilattu -> Peruttu, lähetetään urakoitsijalle sähköpostitse ilmoitus peruutuksesta.
  3. Ehdotettu -> Hylätty, lähetetään urakoitsijalle sähköpostitse ilmoitus hylkäyksestä.
  4. Hylätty -> Ehdotettu, lähetetään urakoitsijalle sähköpostitse ilmoitus tilan muutoksesta.
  5. Tilattu -> Valmis, lähetetään tilaajalle sähköpostitse ilmoitus tilan muutoksesta.
  "
  [db fim email user kohde vanha-kohde sampo-id]
  (let [vanha-tila (:paikkauskohteen-tila vanha-kohde)
        uusi-tila (:paikkauskohteen-tila kohde)
        _ (cond
            ;; Lähetään tilauksesta sähköpostia urakoitsijalle
            (and
              (= "ehdotettu" vanha-tila)
              (= "tilattu" uusi-tila))
            (laheta-sahkoposti fim email sampo-id
                               #{"urakan vastuuhenkilö"}
                               "Paikkauskohde tilattu"
                               (str "Paikkauskohde " (:nimi kohde) " tilattu !"))

            ;; Lähetään perumisesta sähköpostia urakoitsijalle
            (and
              (= "tilattu" vanha-tila)
              (= "ehdotettu" uusi-tila))
            (laheta-sahkoposti fim email sampo-id
                               #{"urakan vastuuhenkilö"}
                               "Paikkauskohde peruttu"
                               (str "Paikkauskohde " (:nimi kohde) " on siirretty tilasta \"tilattu\" tilaan \"ehdotettu\"."))

            ;; Lähetään hylkäämisestä sähköpostia urakoitsijalle
            (and
              (= "ehdotettu" vanha-tila)
              (= "hylatty" uusi-tila))
            (laheta-sahkoposti fim email sampo-id
                               #{"urakan vastuuhenkilö"}
                               "Paikkauskohde hylätty"
                               (str "Paikkauskohde \"" (:nimi kohde) "\" on hylätty."))

            ;; Lähetään perutusta hylkäyksestä sähköpostia urakoitsijalle #{"ely urakanvalvoja" "urakan vastuuhenkilö"}
            (and
              (= "hylatty" vanha-tila)
              (= "ehdotettu" uusi-tila))
            (laheta-sahkoposti fim email sampo-id
                               #{"urakan vastuuhenkilö"}
                               "Paikkauskohteen hylkäys peruttu"
                               (str "Paikkauskohde \"" (:nimi kohde) "\" on siirretty tilasta \"hylätty\" tilaan \"ehdotettu\"."))

            (and
              (= "tilattu" vanha-tila)
              (= "valmis" uusi-tila))
            (laheta-sahkoposti fim email sampo-id
                               #{"ely urakanvalvoja"}
                               "Paikkauskohde valmistunut"
                               (str "Paikkauskohde \"" (:nimi kohde) "\" on valmistunut."))

            :else
            (log/debug (str "Paikkauskohteen: " (:id kohde) " tila ei muuttunut. Sähköposteja ei lähetetä.")))

        ;; Jos paikkauskohteessa on tuhottu tiemerkintää, ilmoitetaan siitä myös sähköpostilla
        _ (when (:tiemerkintaa-tuhoutunut? kohde)
            (ilmoita-tiemerkintaan db fim email user kohde))
        ]
    ;; Siivotaan paikkauskohteesta mahdolliset tiemerkintään liittyvät tiedot pois
    (dissoc kohde :viesti :kopio-itselle? :tiemerkinta-urakka)))

(defn tallenna-paikkauskohde! [db fim email user kohde kehitysmoodi?]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-paikkaukset-paikkauskohteetkustannukset user (:urakka-id kohde))
  (let [_ (log/debug "tallenna-paikkauskohde! :: kohde " (pr-str (dissoc kohde :sijainti)))
        kayttajarooli (roolit/osapuoli user)
        on-kustannusoikeudet? (oikeudet/voi-kirjoittaa? oikeudet/urakat-paikkaukset-paikkauskohteetkustannukset (:urakka-id kohde) user)
        kohde-id (:id kohde)
        vanha-kohde (when kohde-id (first (paikkaus-q/hae-paikkauskohde db {:id kohde-id
                                                                            :urakka-id (:urakka-id kohde)})))
        ;; Haetaan urakan sampo-id sähköpostin lähetystä varten
        urakka-sampo-id (urakat-q/hae-urakan-sampo-id db (:urakka-id kohde))
        ;; Tarkista pakolliset tiedot ja tietojen oikeellisuus
        validointivirheet (paikkauskohde-validi? kohde vanha-kohde kayttajarooli) ;;rooli on null?
        ;; Sähköpostin lähetykset vain kehitysservereillä tässä vaiheessa
        kohde (if kehitysmoodi?
                (tarkista-tilamuutoksen-vaikutukset db fim email user kohde vanha-kohde urakka-sampo-id)
                kohde)
        tr-osoite {::paikkaus/tierekisteriosoite_laajennettu
                   {:harja.domain.tielupa/tie (int (:tie kohde))
                    :harja.domain.tielupa/aosa (int (:aosa kohde))
                    :harja.domain.tielupa/aet (int (:aet kohde))
                    :harja.domain.tielupa/losa (int (:losa kohde))
                    :harja.domain.tielupa/let (int (:let kohde))
                    :harja.domain.tielupa/ajorata (int (or (:ajorata kohde) 0))
                    :harja.domain.tielupa/puoli nil
                    :harja.domain.tielupa/geometria nil
                    :harja.domain.tielupa/karttapvm nil
                    :harja.domain.tielupa/kaista nil}}
        paikkauskohde (merge
                        {::paikkaus/ulkoinen-id (int (:ulkoinen-id kohde))
                         ::paikkaus/urakka-id (:urakka-id kohde)
                         ::muokkaustiedot/luotu (pvm/nyt)
                         ::muokkaustiedot/luoja-id (:id user)
                         ::paikkaus/nimi (:nimi kohde)
                         ::muokkaustiedot/poistettu? (or (:poistettu kohde) false)
                         ::paikkaus/yhalahetyksen-tila (or (:yhalahetyksen-tila kohde) nil)
                         ::paikkaus/virhe (or (:virhe kohde) nil)
                         ::paikkaus/tarkistettu (or (:tarkistettu kohde) nil)
                         ::paikkaus/tarkistaja-id (or (:tarkistaja-id kohde) nil)
                         ::paikkaus/ilmoitettu-virhe (or (:ilmoitettu-virhe kohde) nil)
                         ::paikkaus/alkupvm (:alkupvm kohde)
                         ::paikkaus/loppupvm (:loppupvm kohde)
                         ::paikkaus/tyomenetelma (or (:tyomenetelma kohde) nil)
                         ::paikkaus/pot? (or (:pot? kohde) false)
                         ::paikkaus/paikkauskohteen-tila (:paikkauskohteen-tila kohde)
                         ::paikkaus/suunniteltu-maara (when (:suunniteltu-maara kohde)
                                                        (bigdec (:suunniteltu-maara kohde)))
                         ::paikkaus/yksikko (:yksikko kohde)
                         ::paikkaus/lisatiedot (or (:lisatiedot kohde) nil)
                         ::paikkaus/valmistumispvm (or (:valmistumispvm kohde) nil)
                         ::paikkaus/toteutunut-hinta (or (:toteutunut-hinta kohde) nil)
                         ::paikkaus/tiemerkintaa-tuhoutunut? (or (:tiemerkintaa-tuhoutunut? kohde) nil)
                         ::paikkaus/takuuaika (or (:takuuaika kohde) nil)
                         ::paikkaus/tiemerkintapvm (when (:tiemerkintaa-tuhoutunut? kohde) (pvm/nyt))}
                        (when on-kustannusoikeudet?
                          {::paikkaus/suunniteltu-hinta (bigdec (:suunniteltu-hinta kohde))})
                        (when kohde-id
                          {::paikkaus/id kohde-id
                           ::muokkaustiedot/muokattu (pvm/nyt)
                           ::muokkaustiedot/muokkaaja-id (:id user)})
                        tr-osoite)
        ;; Jos annetulla kohteella on olemassa id, niin päivitetään. Muuten tehdään uusi
        kohde (when (empty? validointivirheet)
                (let [p (specql/upsert! db ::paikkaus/paikkauskohde paikkauskohde)
                      ;; Näitä ei välttämättä tarvitsekaan kääntää
                      ;p (set/rename-keys p paikkaus/specql-avaimet->paikkauskohde)
                      #_ p #_(-> p
                            (assoc :tie (get-in p [::paikkaus/tierekisteriosoite_laajennettu ::paikkaus/tie]))
                            (assoc :aosa (get-in p [::paikkaus/tierekisteriosoite_laajennettu ::paikkaus/aosa]))
                            (assoc :aet (get-in p [::paikkaus/tierekisteriosoite_laajennettu ::paikkaus/aet]))
                            (assoc :losa (get-in p [::paikkaus/tierekisteriosoite_laajennettu ::paikkaus/losa]))
                            (assoc :let (get-in p [::paikkaus/tierekisteriosoite_laajennettu ::paikkaus/let]))
                            (assoc :ajorata (get-in p [::paikkaus/tierekisteriosoite_laajennettu ::paikkaus/ajorata]))
                            (dissoc ::paikkaus/tierekisteriosoite_laajennettu))]
                  {:id (::paikkaus/id p)
                   :paikkauskohteen-tila (::paikkaus/paikkauskohteen-tila p)}))

        _ (log/debug "kohde: " (pr-str kohde))
        _ (log/debug "validaatiovirheet" (pr-str (empty? validointivirheet)) (pr-str validointivirheet))
        ]
    (if (empty? validointivirheet)
      kohde
      (throw+ {:type "Validaatiovirhe"
               :virheet {:koodi "ERROR" :viesti validointivirheet}}))))

(defn poista-paikkauskohde! [db user kohde]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-paikkaukset-paikkauskohteetkustannukset user (:urakka-id kohde))
  (let [id (:id kohde)
        ;; Tarkistetaan, että haluttu paikkauskohde on olemassa eikä sitä ole vielä poistettu
        poistettava (first (paikkaus-q/hae-paikkauskohde db {:id (:id kohde) :urakka-id (:urakka-id kohde)}))
        _ (paikkaus-q/poista-paikkauskohde! db id)]
    (if (empty? poistettava)
      (throw+ {:type "Error"
               :virheet [{:koodi "ERROR" :viesti "Paikkauskohdetta ei voitu poistaa, koska sitä ei löydy."}]})
      ;; Palautetaan poistettu paikkauskohde
      (assoc poistettava :poistettu true))))

(defn- kasittele-excel [db fim email urakka-id kayttaja req kehitysmoodi?]
  (let [workbook (xls/load-workbook-from-file (:path (bean (get-in req [:params "file" :tempfile]))))
        paikkauskohteet (p-excel/erottele-paikkauskohteet workbook)
        ;; Urakalla ei saa olla kahta saman nimistä paikkauskohdetta. Niinpä varmistetaan, ettei näin ole ja jos ei ole, niin tallennetaan paikkauskohde kantaan
        kohteet (when (not (empty? paikkauskohteet))
                  (keep
                    (fn [p]
                      (let [;; Excelistä ei aseteta paikkauskohteelle tilaa, joten asetetaan se "ehdotettu" tilaan tässä
                            p (-> p
                                  (assoc :urakka-id urakka-id)
                                  (assoc :paikkauskohteen-tila "ehdotettu"))
                            kohde (paikkaus-q/onko-kohde-olemassa-nimella? db (:nimi p) urakka-id)]
                        (if (empty? kohde)
                          (try+

                            (tallenna-paikkauskohde! db fim email kayttaja p kehitysmoodi?)

                            (catch [:type "Validaatiovirhe"] e
                              ;; TODO: Tarkista, että validaatiovirheiden ja olemassa olevien virheiden formaatti on sama
                              {:virhe (get-in e [:virheet :viesti])
                               :paikkauskohde (:nimi p)}))
                          {:virhe "Urakalta löytyy jo kohde samalla nimellä"
                           :paikkauskohde (:nimi p)})))
                    paikkauskohteet))
        tallennetut (filterv #(nil? (:virhe %)) kohteet)
        virheet (filterv #(some? (:virhe %)) kohteet)
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
                                {:virheet [{:virhe "Excelistä ei löydetty paikkauskohteita!"}]}))]
    ;; Vielä ei selvää, halutaanko tallentaa mitään, jos seassa virheellisiä.
    ;; Oletetaan toistaiseksi, että halutaan tallentaa ne, joissa ei ole virheitä
    ;; ja palautetaan tieto myös virheellistä kohteista.
    (if (> (count tallennetut) 0)
      {:status 200
       :headers {"Content-Type" "application/json; charset=UTF-8"}
       :body body}
      {:status 400
       :headers {"Content-Type" "application/json; charset=UTF-8"}
       :body body})))

(defn vastaanota-excel [db fim email req kehitysmoodi?]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-paikkaukset-paikkauskohteetkustannukset
                                  (:kayttaja req)
                                  (Integer/parseInt (get (:params req) "urakka-id")))
  (let [urakka-id (Integer/parseInt (get (:params req) "urakka-id"))
        kayttaja (:kayttaja req)]
    ;; Tarkistetaan, että kutsussa on mukana urakka ja kayttaja
    (if (and (not (nil? urakka-id))
             (not (nil? kayttaja)))
      (kasittele-excel db fim email urakka-id kayttaja req kehitysmoodi?)
      (throw+ {:type "Error"
               :virheet [{:koodi "ERROR" :viesti "Ladatussa tiedostossa virhe."}]}))))

(defrecord Paikkauskohteet [kehitysmoodi?]
  component/Lifecycle
  (start [this]
    (let [http (:http-palvelin this)
          fim (:fim this)
          email (:sonja-sahkoposti this)
          db (:db this)
          excel (:excel-vienti this)]
      (julkaise-palvelu http :paikkauskohteet-urakalle
                        (fn [user tiedot]
                          (paikkaus-q/paikkauskohteet db user tiedot)))
      (julkaise-palvelu http :tallenna-paikkauskohde-urakalle
                        (fn [user kohde]
                          (tallenna-paikkauskohde! db fim email user kohde kehitysmoodi?)))
      (julkaise-palvelu http :laske-paikkauskohteen-pituus
                        (fn [user kohde]
                          (paikkaus-q/laske-paikkauskohteen-pituus db kohde)))
      (julkaise-palvelu http :poista-paikkauskohde
                        (fn [user kohde]
                          (poista-paikkauskohde! db user kohde)))
      (julkaise-palvelu http :hae-paikkauskohteen-yhteyshenkilot
                        (fn [user urakka-id]
                          (yhteyshenkilot/hae-urakan-yhteyshenkilot (:db this) user urakka-id true)))
      (julkaise-palvelu http :lue-paikkauskohteet-excelista
                        (wrap-multipart-params (fn [req] (vastaanota-excel db fim email req kehitysmoodi?)))
                        {:ring-kasittelija? true})
      (julkaise-palvelu http :tallenna-kasinsyotetty-paikkaus
                        (fn [user paikkaus]
                          (paikkaus-q/tallenna-kasinsyotetty-paikkaus db user paikkaus)))
      (julkaise-palvelu http :poista-kasinsyotetty-paikkaus
                        (fn [user paikkaus]
                          (do
                            (println ":poista-kasinsyotetty-paikkaus paikkaus:" (pr-str paikkaus))
                            (paikkaus-q/poista-kasin-syotetty-paikkaus
                              db (:id user) (:urakka-id paikkaus) (:id paikkaus)))))
      (julkaise-palvelu http :hae-paikkauskohteen-tiemerkintaurakat
                        (fn [user tiedot]
                          (hae-paikkauskohteen-tiemerkintaurakat db user tiedot)))
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
      :tallenna-kasinsyotetty-paikkaus
      :poista-kasinsyotetty-paikkaus
      :hae-paikkauskohteen-tiemerkintaurakat
      (when (:excel-vienti this)
        (excel-vienti/poista-excel-kasittelija! (:excel-vienti this) :paikkauskohteet-urakalle-excel)))
    this))
