(ns harja.palvelin.palvelut.yllapitokohteet.paikkauskohteet
  (:require [com.stuartsierra.component :as component]
            [cheshire.core :as cheshire]
            [hiccup.core :refer [html]]
            [slingshot.slingshot :refer [throw+ try+]]
            [clojure.spec.alpha :as s]
            [taoensso.timbre :as log]
            [clojure.set :as set]
            [dk.ative.docjure.spreadsheet :as xls]
            [ring.middleware.multipart-params :refer [wrap-multipart-params]]
            [harja.domain.oikeudet :as oikeudet]
            [harja.domain.tierekisteri.validointi :as tr-validointi]
            [harja.domain.roolit :as roolit]
            [harja.domain.paikkaus :as paikkaus]
            [harja.domain.muokkaustiedot :as muokkaustiedot]
            [harja.domain.tierekisteri :as tr]
            [harja.pvm :as pvm]
            [harja.fmt :as fmt]
            [harja.kyselyt.paikkaus :as paikkaus-q]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.yllapitokohteet :as yllapitokohteet-q]
            [harja.kyselyt.sopimukset :as sopimukset-q]
            [harja.kyselyt.tieverkko :as q-tr]
            [harja.palvelin.komponentit.fim :as fim]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.palvelin.integraatiot.sahkoposti :as sahkoposti]
            [harja.tyokalut.html :as html-tyokalut]
            [harja.palvelin.palvelut.yhteyshenkilot :as yhteyshenkilot]
            [harja.palvelin.palvelut.yllapitokohteet.paikkauskohteet-excel :as p-excel]
            [harja.palvelin.komponentit.excel-vienti :as excel-vienti]
            [specql.core :as specql]
            [harja.kyselyt.konversio :as konversio]
            [clojure.java.jdbc :as jdbc]))

(defn validi-pvm-vali? [validointivirheet alku loppu]
  (if (and (not (nil? alku)) (not (nil? loppu)) (.after alku loppu))
    (conj validointivirheet "Loppuaika tulee ennen alkuaikaa.")
    validointivirheet))

(defn- sallittu-tilamuutos? [uusi vanha rooli]
  (let [uusi-tila (:paikkauskohteen-tila uusi)
        vanha-tila (:paikkauskohteen-tila vanha)
        ehdotettu? #(= "ehdotettu" %)
        tilattu? #(= "tilattu" %)
        hylatty? #(= "hylatty" %)
        valmis? #(= "valmis" %)
        toteumia? (and (not (nil? (:toteumien-maara uusi))) (< 0 (:toteumien-maara uusi)))]
    ;; Kaikille sallitut tilamuutokset, eli ei muutosta tai uusi paikkauskohde.
    (if (or (= uusi-tila vanha-tila)
            (nil? vanha-tila))
      true
      (cond
        ;; Tilaajan sallitut tilamuutokset
        (= rooli :tilaaja) (or
                             (and (ehdotettu? vanha-tila) (or (tilattu? uusi-tila) (hylatty? uusi-tila)))
                             (and (hylatty? vanha-tila) (ehdotettu? uusi-tila))
                             ;; Tilattua kohdetta ei saa perua, jos sille on lisätty toteumia.
                             (and (tilattu? vanha-tila) (ehdotettu? uusi-tila) (not toteumia?))
                             (and (tilattu? vanha-tila) (valmis? uusi-tila))
                             ;; Valmiiksi merkityn kohteen muutos tilatuksi sallitaan, jotta siihen voi tehdä vielä muokkauksia
                             (and (valmis? vanha-tila) (tilattu? uusi-tila)))
        ;; Urakoitsija saa merkata tilatun valmiiksi
        (= rooli :urakoitsija) (or
                                 (and (tilattu? vanha-tila) (valmis? uusi-tila))
                                 ;; Valmiiksi merkityn kohteen muutos tilatuksi sallitaan, jotta siihen voi tehdä vielä muokkauksia
                                 (and (valmis? vanha-tila) (tilattu? uusi-tila)))
        :default
        false))))

(defn validi-paikkauskohteen-tilamuutos? [validointivirheet uusi vanha rooli]
  (if (sallittu-tilamuutos? uusi vanha rooli)
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

(defn- validoi-ulkoinen-id
  "Varmistetaan, että samalla ulkoisella id:llä ei ole aiempaa paikkauskohdetta."
  [db validointivirheet uusi-kohde vanha-kohde]
  (let [kohteet-tietokannasta (paikkaus-q/hae-paikkauskohteet-ulkoisella-idlla
                                db
                                {:id (if (= (:id uusi-kohde) (:id vanha-kohde))
                                       (:id uusi-kohde)
                                       nil)
                                 :ulkoinen-id (konversio/konvertoi->int (:ulkoinen-id uusi-kohde))
                                 :urakka-id (:urakka-id uusi-kohde)})]
    (if (empty? kohteet-tietokannasta)
      validointivirheet
      (conj validointivirheet
        (str "Paikkauskohteen Nro: '" (konversio/konvertoi->int (:ulkoinen-id uusi-kohde)) "' on jo käytössä.
        Numeron täytyy olla yksilöllinen.
        Samalla numerolla löytyy kohde: '" (:nimi (first kohteet-tietokannasta)) "' alkanut: " (pvm/pvm (:alkupvm (first kohteet-tietokannasta))))))))

(s/def ::nimi (s/and string? #(validi-nimi? %)))
(s/def ::alkupvm (s/and #(inst? %) #(validi-aika? %)))
(s/def ::loppupvm (s/and #(inst? %) #(validi-aika? %)))
(s/def ::paikkauskohteen-tila (s/and string? #(validi-paikkauskohteen-tila? %)))
(s/def ::tyomenetelma (s/and number? pos?))
(s/def ::suunniteltu-maara (s/and number? pos?))
(s/def ::suunniteltu-hinta (s/and number? pos?))
(s/def ::ulkoinen-id (s/and number? pos?))
(s/def ::yksikko paikkaus/paikkauskohteiden-yksikot)

(defn paikkauskohde-validi? [db kohde vanha-kohde rooli]
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
                                  (conj virheet "Paikkauskohteen Nro puuttuu"))
                                (if (and (s/valid? ::alkupvm (:alkupvm kohde))
                                         (s/valid? ::loppupvm (:loppupvm kohde)))
                                  (validi-pvm-vali? virheet (:alkupvm kohde) (:loppupvm kohde))
                                  virheet)
                                (tr-validointi/validoi-tieosoite virheet (:tie kohde) (:aosa kohde) (:losa kohde) (:aet kohde) (:let kohde))
                                (validi-paikkauskohteen-tilamuutos? virheet kohde vanha-kohde rooli)
                            (validoi-ulkoinen-id db virheet kohde vanha-kohde))]
    validointivirheet))

(defn- laheta-sahkoposti [fim email sampo-id roolit viestin-otsikko viestin-vartalo]
  (let [ ;; Testausta varten varmistetaan, että fim on annettu
        vastaanottajat (when fim (fim/hae-urakan-kayttajat-jotka-roolissa fim sampo-id roolit))
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
            viestin-vartalo
            {})
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
        otsikko (str "Kohteen " (:nimi tiedot) " paikkaustyö on valmistunut")
        viesti (html
                 [:div
                  [:p otsikko]
                  (html-tyokalut/tietoja [["Kohde: " (:nimi tiedot)]
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

(defn- muodosta-viesti 
  [tilasiirtyma {{:keys [tie aosa aet losa let]} :tierekisteriosoite
                 :keys [kohteen-nimi urakan-nimi vastaanottajan-nimi pvm ely-id urakka-id]}]
  (html (into [:div] 
              (keep identity)
              [[:p (str "Hei " (or vastaanottajan-nimi ""))]
               (case tilasiirtyma 
                 :ehdotettu->tilattu [:p (str "Urakan " urakan-nimi " paikkauskohde " kohteen-nimi " on tilattu " pvm ".")]
                 :ehdotettu->hylatty [:p (str "Urakan " urakan-nimi " paikkauskohde " kohteen-nimi " on hylätty " pvm ".")]
                 :hylatty->ehdotettu [:p (str "Urakan " urakan-nimi " paikkauskohteen " kohteen-nimi " hylkäys on peruttu " pvm ".")]
                 :tilattu->valmis [:p (str "Urakan " urakan-nimi " paikkauskohde " kohteen-nimi " on valmis " pvm ".")]
                 :tilattu->ehdotettu [:p (str "Urakan " urakan-nimi " paikkauskohde " kohteen-nimi " on peruttu " pvm ".")]
                 nil)
               (case tilasiirtyma 
                 :ehdotettu->hylatty [:p "Tarkemmat tiedot hylkäämiseen johtaneista syistä saat urakanvalvojalta"]
                 :hylatty->ehdotettu [:p "Tarkemmat tiedot hylkäämiseen johtaneista syistä ja hylkäyksen perumisesta saat urakanvalvojalta"]
                 :tilattu->ehdotettu [:p "Tarkemmat tiedot peruuttamiseen johtaneista syistä saat urakanvalvojalta"]
                 nil)
               [:p (str "Paikkauskohde sijaitsee " tie " - " aet "/" aosa " " let "/" losa)]
               [:p (str "Voit tarkastella paikkauskohdetta tarkemmin https://extranet.vayla.fi/harja/#urakat/paikkaukset?&hy=" ely-id "&u=" urakka-id)]
               [:p "Tämä on automaattinen viesti HARJA -järjestelmästä, älä vastaa tähän viestiin."]])))

(defn tarkista-tilamuutoksen-vaikutukset
  "Kun paikkauskohteen tila muuttuu, niin on mahdollisuus että urakoitsijalle tai tilaajalle lähetetään sähköpostia.
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
        urakan-nimi (-> (urakat-q/hae-yksittainen-urakka db {:urakka_id (:urakka-id kohde)}) 
                          first
                          :nimi)
        ely-id (-> (urakat-q/hae-urakan-ely db {:urakkaid (:urakka-id kohde)})
                              first
                              :id)
        tilasiirtyma (keyword (str vanha-tila "->" uusi-tila))
        kohde (cond-> kohde
                      ;; Asetetaan tilauspäivämäärä, kun tilataan
                      (and
                        (= "ehdotettu" vanha-tila)
                        (= "tilattu" uusi-tila))
                      (assoc :tilattupvm (pvm/nyt))

                      ;; Poistetaan tilauspäivämäärä, kun perutaan
                      (and
                        (= "tilattu" vanha-tila)
                        (= "hylatty" uusi-tila))
                      (assoc :tilattupvm nil)

                      ;; Valmiiksi merkitty kohde muutetaan tilatuksi - poistetaan valmistumispäivämäärä ja tiemerkintä tiedot
                      (and
                        (= "valmis" vanha-tila)
                        (= "tilattu" uusi-tila))
                      (assoc :valmistumispvm nil
                             :pot-valmistumispvm nil
                             :tiemerkintapvm nil
                             :tiemerkintaa-tuhoutunut? nil)
                      )

        otsikko (case tilasiirtyma
                  :ehdotettu->tilattu "Paikkauskohde tilattu"
                  :tilattu->ehdotettu "Paikkauskohde peruttu"
                  :ehdotettu->hylatty "Paikkauskohde hylätty"
                  :hylatty->ehdotettu "Paikkauskohteen hylkäys peruttu"
                  :tilattu->valmis "Paikkauskohde valmistunut"
                  (log/debug (str "Paikkauskohteen: " (:id kohde) " tila ei muuttunut. Sähköpostiotsikkoa ei määritetä.")))
        roolit (case tilasiirtyma
                 :tilattu->valmis #{"ely urakanvalvoja"}
                 #{"urakan vastuuhenkilö"})
        ;; Testausta varten jätetään mahdollisuus, että fimiä ei ole asennettu
        vastaanottajat (try
                         (when fim
                              (fim/hae-urakan-kayttajat-jotka-roolissa fim sampo-id roolit))
                         (catch Exception e
                           (log/error e "Fimiin ei saatu yhteyttä.")))
        viesti (muodosta-viesti
                tilasiirtyma 
                {:kohteen-nimi (:nimi kohde)
                 :pvm (pvm/pvm (pvm/nyt))
                                              :urakan-nimi urakan-nimi
                                              :ely-id ely-id
                                              :urakka-id (:urakka-id kohde)
                                              :tierekisteriosoite (select-keys kohde [:tie :aosa :aet :let :losa]) })]
       (if (empty? vastaanottajat)
         (log/debug (str "Paikkauskohteelle: " (:id kohde) " / " (:nimi kohde) " ei löytynyt sähköpostin vastaanottajaa. Sähköposteja ei lähetetä."))
         (case tilasiirtyma
               ;; Lähetään tilauksesta sähköpostia urakoitsijalle
               (:ehdotettu->tilattu
                 :tilattu->ehdotettu
                 :ehdotettu->hylatty
                 :hylatty->ehdotettu
                 :tilattu->valmis)
               (laheta-sahkoposti fim email sampo-id
                                  roolit
                                  otsikko
                                  viesti)
               (log/debug (str "Paikkauskohteen: " (:id kohde) " / " (:nimi kohde) "  tila ei muuttunut. Sähköposteja ei lähetetä."))))

        ;; Jos paikkauskohteessa on tuhottu tiemerkintää, ilmoitetaan siitä myös sähköpostilla
        (when (and (nil? (:tiemerkintapvm vanha-kohde))
                     (:tiemerkintaa-tuhoutunut? kohde))
            (ilmoita-tiemerkintaan db fim email user kohde))

    ;; Siivotaan paikkauskohteesta mahdolliset tiemerkintään liittyvät tiedot pois
    (dissoc kohde :viesti :kopio-itselle? :tiemerkinta-urakka)))

(defn tyomenetelma-str->id [db nimi]
  (::paikkaus/tyomenetelma-id (first (paikkaus-q/hae-tyomenetelman-id db nimi))))

(defn tarkista-pot-raportointi
  "Mikäli paikkauskohteelle on merkattu :pot? true, tehdään paikkauskohteesta pot ilmoitus.
  POT vaatii lisäyksen yllapitokohde -tauluun sekä ylläapitokohteen_aikataulu -tauluun."
  [db uusi-kohde vanha-kohde kayttaja-id]
  (let [muodosta-yllapitokohde? (if (and (:pot? uusi-kohde) (not (:yllapitokohde-id vanha-kohde)))
                                  true
                                  false)
        sopimus-id (:id (first (sopimukset-q/hae-urakan-paasopimus db (:urakka-id uusi-kohde))))
        ;; Jos paikkauskohteen tietoja muutetaan tieosoitteen osalta, myös ylläpitokohteen tietojen pitää muuttua, joten arvoidaan
        ;; onko paikkauskohteen tieosoite vaihtunut
        muokkaa-yllapitokohdetta? (and
                                    ;; Uudella ja vanhalla kohteella on olemassa ylläpitokohde
                                    (:yllapitokohde-id uusi-kohde) (:yllapitokohde-id vanha-kohde)
                                    ;; Uuden ja vanhan kohteen tierekisteriosoite on muuttunut
                                    (not=
                                      (hash (str (:tie uusi-kohde) (:aosa uusi-kohde) (:aet uusi-kohde) (:losa uusi-kohde) (:let uusi-kohde) ) )
                                      (hash (str (:tie vanha-kohde) (:aosa vanha-kohde) (:aet vanha-kohde) (:losa vanha-kohde) (:let vanha-kohde) ) )))
        yllapitokohde (when muodosta-yllapitokohde?
                        (yllapitokohteet-q/luo-yllapitokohde<! db
                                                               {:urakka (:urakka-id uusi-kohde)
                                                                :sopimus sopimus-id
                                                                :kohdenumero (:ulkoinen-id uusi-kohde)
                                                                :nimi (:nimi uusi-kohde)
                                                                :yotyo (:yotyo uusi-kohde)
                                                                :tr_numero (:tie uusi-kohde)
                                                                :tr_alkuosa (:aosa uusi-kohde)
                                                                :tr_alkuetaisyys (:aet uusi-kohde)
                                                                :tr_loppuosa (:losa uusi-kohde)
                                                                :tr_loppuetaisyys (:let uusi-kohde)
                                                                ;; Riippumatta siitä, mitä paikkauskohteelle on valittu ajorataa ei merkitä ylläpitokohteelle
                                                                :tr_ajorata nil
                                                                :tr_kaista nil
                                                                :keskimaarainen_vuorokausiliikenne nil
                                                                :yllapitoluokka nil
                                                                :yllapitokohdetyyppi "paallyste"
                                                                :yllapitokohdetyotyyppi "paallystys"
                                                                :vuodet (konversio/seq->array [(pvm/vuosi (pvm/nyt))]) ;; En tiedä mitä tänne pitäisi laittaa
                                                                }))
        yllapitokohde (if muokkaa-yllapitokohdetta?
                        (yllapitokohteet-q/paivita-yllapitokohde<! db
                          {:kohdenumero (:ulkoinen-id uusi-kohde)
                           :nimi (:nimi uusi-kohde)
                           :tunnus nil
                           :yotyo (:yotyo uusi-kohde)
                           :tr_numero (:tie uusi-kohde)
                           :tr_alkuosa (:aosa uusi-kohde)
                           :tr_alkuetaisyys (:aet uusi-kohde)
                           :tr_loppuosa (:losa uusi-kohde)
                           :tr_loppuetaisyys (:let uusi-kohde)
                           ;; Riippumatta siitä, mitä paikkauskohteelle on valittu ajorataa ei merkitä ylläpitokohteelle
                           :tr_ajorata nil
                           :tr_kaista nil
                           :keskimaarainen_vuorokausiliikenne nil
                           :yllapitoluokka nil})
                        yllapitokohde)
        yllapitokohde-id (:id yllapitokohde)
        ;; Luodaan ensin tyhjä ylläpitikohteen aikataulu
        _ (when muodosta-yllapitokohde?
            (yllapitokohteet-q/luo-yllapitokohteelle-tyhja-aikataulu<! db {:yllapitokohde yllapitokohde-id}))
        ;; Merkitään sitten tiedossaolevat aikataulut
        _ (when muodosta-yllapitokohde?
            (yllapitokohteet-q/paivita-yllapitokohteen-paallystysaikataulu! db {:id yllapitokohde-id
                                                                                :kohde_alku (:alkupvm uusi-kohde)
                                                                                :muokkaaja kayttaja-id
                                                                                :paallystys_alku (:alkupvm uusi-kohde)
                                                                                :paallystys_loppu (:loppupvm uusi-kohde)
                                                                                :kohde_valmis nil
                                                                                :valmis_tiemerkintaan nil}))
        uusi-kohde (if muodosta-yllapitokohde?
                     (assoc uusi-kohde :yllapitokohde-id yllapitokohde-id)
                     uusi-kohde)
        ;; Jos päivitetään paikkauskohdetta, jolle on jo yllapitokohteen aikataulut luotu, niin koitetaan päivittää niitä
        ypka (when (:yllapitokohde-id vanha-kohde)
               (first (yllapitokohteet-q/hae-yllapitokohteen-aikataulu db {:id (:yllapitokohde-id vanha-kohde)})))
        _ (when (:yllapitokohde-id vanha-kohde)
            (yllapitokohteet-q/paivita-yllapitokohteen-paallystysaikataulu! db {:id (:yllapitokohde-id vanha-kohde)
                                                                                :kohde_alku (:kohde-alku ypka)
                                                                                :muokkaaja kayttaja-id
                                                                                :paallystys_alku (:pot-tyo-alkoi uusi-kohde)
                                                                                :paallystys_loppu (:pot-tyo-paattyi uusi-kohde)
                                                                                :kohde_valmis (or (:pot-valmistumispvm uusi-kohde) (:kohde-valmis ypka))
                                                                                :valmis_tiemerkintaan (:valmis-tiemerkintaan ypka)}))
        ]
    uusi-kohde))

(defn tallenna-paikkauskohde! [db fim email user kohde kehitysmoodi?]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-paikkaukset-paikkauskohteetkustannukset user (:urakka-id kohde))
  (let [_ (log/debug "tallenna-paikkauskohde! :: kohde " (pr-str (dissoc kohde :sijainti)))
        kayttajarooli (roolit/osapuoli user)
        on-kustannusoikeudet? (oikeudet/voi-kirjoittaa? oikeudet/urakat-paikkaukset-paikkauskohteetkustannukset (:urakka-id kohde) user)
        kohde-id (:id kohde)
        vanha-kohde (when kohde-id (first (paikkaus-q/hae-paikkauskohde db {:id kohde-id
                                                                            :urakka-id (:urakka-id kohde)})))
        ;; Muutetaan tarvittaessa työmenetelmä teksistä ID:ksi.
        annettu-tyomenetelma (:tyomenetelma kohde)
        kohde (if (string? annettu-tyomenetelma)
                (assoc kohde :tyomenetelma (paikkaus-q/hae-tyomenetelman-id db annettu-tyomenetelma))
                kohde)
        ;; Haetaan urakan sampo-id sähköpostin lähetystä varten
        urakka-sampo-id (urakat-q/hae-urakan-sampo-id db (:urakka-id kohde))
        ;; Tarkista pakolliset tiedot ja tietojen oikeellisuus
        validointivirheet (paikkauskohde-validi? db kohde vanha-kohde kayttajarooli) ;;rooli on null?
        kohde (tarkista-tilamuutoksen-vaikutukset db fim email user kohde vanha-kohde urakka-sampo-id)
        ;; Mikäli paikkauskohde halutaan raportoida pot lomakkeella, tehdään samalla yllapitokohde tauluun merkintä
        kohde (tarkista-pot-raportointi db kohde vanha-kohde (:id user))
        ;; Kohteen valmistumispäivä kaivellaan pot raportoitavalla vähä eri tavalla
        valmistumispvm (or (and (:pot? kohde) (:pot-valmistumispvm kohde)) (:valmistumispvm kohde) nil)
        tr-osoite {::paikkaus/tierekisteriosoite_laajennettu
                   {:harja.domain.tielupa/tie (konversio/konvertoi->int (:tie kohde))
                    :harja.domain.tielupa/aosa (konversio/konvertoi->int (:aosa kohde))
                    :harja.domain.tielupa/aet (konversio/konvertoi->int (:aet kohde))
                    :harja.domain.tielupa/losa (konversio/konvertoi->int (:losa kohde))
                    :harja.domain.tielupa/let (konversio/konvertoi->int (:let kohde))
                    :harja.domain.tielupa/ajorata (konversio/konvertoi->int (or (:ajorata kohde) 0))
                    :harja.domain.tielupa/puoli nil
                    :harja.domain.tielupa/geometria nil
                    :harja.domain.tielupa/karttapvm nil
                    :harja.domain.tielupa/kaista nil}}
        paikkauskohde (merge
                        {::paikkaus/ulkoinen-id (konversio/konvertoi->int (:ulkoinen-id kohde))
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
                         ::paikkaus/valmistumispvm valmistumispvm
                         ::paikkaus/tilattupvm (or (:tilattupvm kohde) nil)
                         ::paikkaus/toteutunut-hinta (when (:toteutunut-hinta kohde)
                                                       (bigdec (:toteutunut-hinta kohde)))
                         ::paikkaus/tiemerkintaa-tuhoutunut? (or (:tiemerkintaa-tuhoutunut? kohde) nil)
                         ::paikkaus/takuuaika (when (:takuuaika kohde) (bigdec (:takuuaika kohde)))
                         ::paikkaus/tiemerkintapvm (when (:tiemerkintaa-tuhoutunut? kohde) (pvm/nyt))
                         ::paikkaus/yllapitokohde-id (:yllapitokohde-id kohde)}
                        (when on-kustannusoikeudet?
                          {::paikkaus/suunniteltu-hinta (bigdec (or (:suunniteltu-hinta kohde) 0))})
                        (when kohde-id
                          {::paikkaus/id kohde-id
                           ::muokkaustiedot/muokattu (pvm/nyt)
                           ::muokkaustiedot/muokkaaja-id (:id user)})
                        tr-osoite)
        ;; Jos annetulla kohteella on olemassa id, niin päivitetään. Muuten tehdään uusi
        kohde (when (empty? validointivirheet)
                (let [p (specql/upsert! db ::paikkaus/paikkauskohde paikkauskohde)
                      p (set/rename-keys p paikkaus/specql-avaimet->paikkauskohde)
                      p (-> p
                            (assoc :tie (get-in p [::paikkaus/tierekisteriosoite_laajennettu :harja.domain.tielupa/tie]))
                            (assoc :aosa (get-in p [::paikkaus/tierekisteriosoite_laajennettu :harja.domain.tielupa/aosa]))
                            (assoc :aet (get-in p [::paikkaus/tierekisteriosoite_laajennettu :harja.domain.tielupa/aet]))
                            (assoc :losa (get-in p [::paikkaus/tierekisteriosoite_laajennettu :harja.domain.tielupa/losa]))
                            (assoc :let (get-in p [::paikkaus/tierekisteriosoite_laajennettu :harja.domain.tielupa/let]))
                            (assoc :ajorata (get-in p [::paikkaus/tierekisteriosoite_laajennettu :harja.domain.tielupa/ajorata]))
                            (dissoc ::paikkaus/tierekisteriosoite_laajennettu :virhe))]
                  p))

        _ (log/debug "kohde: " (pr-str kohde))
        _ (log/debug "validaatiovirheet:" (pr-str (not (empty? validointivirheet))) (pr-str validointivirheet))
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
                                       (when (> (count virheet) 0)
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

(def paikkauksen-domain-avaimet
  [::paikkaus/alkuaika
   ::paikkaus/loppuaika
   ::tr/tie
   ::paikkaus/ajorata
   ::tr/aosa
   ::tr/aet
   ::tr/losa
   ::tr/let
   ::paikkaus/leveys
   ::paikkaus/ajourat
   ::paikkaus/reunat
   ::paikkaus/ajouravalit
   ::paikkaus/keskisaumat
   ::paikkaus/massatyyppi
   ::paikkaus/raekoko
   ::paikkaus/kuulamylly
   ::paikkaus/massamaara
   ::paikkaus/pinta-ala])

(defn- tee-tr-osoite [paikkaus]
  (-> paikkaus
    (assoc ::paikkaus/tierekisteriosoite
      (select-keys paikkaus [::tr/tie ::tr/aosa ::tr/aet ::tr/losa ::tr/let]))
    (dissoc ::tr/tie ::tr/aosa ::tr/aet ::tr/losa ::tr/let)))

(defn- tee-tienkohta [{::paikkaus/keys [ajourat ajorata ajouravalit reunat keskisaumat] :as paikkaus}]
  (-> paikkaus
    (assoc ::paikkaus/tienkohdat
      (merge {::paikkaus/ajorata ajorata}
        (when ajourat {::paikkaus/ajourat [ajourat]})
        (when ajouravalit {::paikkaus/ajouravalit [ajouravalit]})
        (when reunat {::paikkaus/reunat [reunat]})
        (when keskisaumat {::paikkaus/keskisaumat [keskisaumat]})))
    (dissoc ::paikkaus/ajourat ::paikkaus/ajorata ::paikkaus/ajouravalit ::paikkaus/reunat ::paikkaus/keskisaumat)))

(defn- yrita-tehda-sijainti [{::tr/keys [tie aosa aet losa let] :as paikkaus} db]
  (if (and tie aosa aet losa let)
    (assoc paikkaus ::paikkaus/sijainti
      (q-tr/tierekisteriosoite-viivaksi db {:tie tie :aosa aosa
                                            :aet aet :losa losa
                                            :loppuet let}))
    paikkaus))

(defn- tee-pituus [{::tr/keys [tie aosa aet losa let] :as paikkaus} db]
  (assoc paikkaus :pituus
    (:pituus (paikkaus-q/laske-paikkauskohteen-pituus db {:tie tie :aosa aosa :aet aet :losa losa :let let}))))

(defn- laske-massamaara
  "Laskee massamäärän perustuen kohteen kokonaismassamäärään sekä rivin suhteellisen pinta-alan osuuteen kokonaispinta-alasta"
  [paikkaus urem-kok-massamaara kaikki-pinta-ala-yhteensa]
  (when (and (number? (::paikkaus/pinta-ala paikkaus))
             (number? kaikki-pinta-ala-yhteensa)
             (number? urem-kok-massamaara))
    (bigdec
      (with-precision 6
        ;; Rivikohtainen massamäärä saadaan laskemalla paikkausrivin pinta-alan
        ;; suhteellinen osuus ja kerrotaan se kohteen kokonaismassamäärällä
        (*
          (with-precision 8 (/ (::paikkaus/pinta-ala paikkaus)
                               kaikki-pinta-ala-yhteensa))
          urem-kok-massamaara)))))

(defn- laske-massamenekki [{pinta-ala ::paikkaus/pinta-ala
                            massamaara ::paikkaus/massamaara :as paikkaus}]
  (when (and (number? pinta-ala) (number? massamaara))
    (paikkaus/massamaara-ja-pinta-ala->massamenekki massamaara pinta-ala)))

(defn- tee-pinta-ala [{pituus :pituus
                       leveys ::paikkaus/leveys :as paikkaus}]
  (cond-> paikkaus
    (and (number? pituus) (number? leveys))
    (assoc ::paikkaus/pinta-ala (* pituus leveys))

    true
    (dissoc :pituus)))

(defn- muuta-arvot [paikkaus avaimet fn]
  (reduce #(update % %2 fn) paikkaus avaimet))

(def intattavat-avaimet
  [::tr/tie
   ::paikkaus/ajorata
   ::tr/aosa
   ::tr/aet
   ::tr/losa
   ::tr/let
   ::paikkaus/ajourat
   ::paikkaus/ajouravalit
   ::paikkaus/reunat
   ::paikkaus/keskisaumat
   ::paikkaus/raekoko])

(def bigdecattavat-avaimet [::paikkaus/massamaara
                            ::paikkaus/leveys
                            ::paikkaus/pinta-ala])


(defn validoi-urem-excel-paikkaus [{alkuaika ::paikkaus/alkuaika
                                    loppuaika ::paikkaus/loppuaika
                                    {tie ::tr/tie
                                     aosa ::tr/aosa
                                     aet ::tr/aet
                                     losa ::tr/losa
                                     loppuet ::tr/let} ::paikkaus/tierekisteriosoite
                                    massatyyppi ::paikkaus/massatyyppi
                                    raekoko ::paikkaus/raekoko
                                    kuulamylly ::paikkaus/kuulamylly
                                    pinta-ala ::paikkaus/pinta-ala}]
  (cond-> []
    (not (s/valid? ::paikkaus/alkuaika alkuaika)) (conj "Alkuaika puuttuu tai on virheellinen")
    (not (s/valid? ::paikkaus/loppuaika loppuaika)) (conj "Loppuaika puuttuu tai on virheellinen")
    (not (s/valid? ::tr/numero tie)) (conj "Tienumero puuttuu tai on virheellinen")
    (not (s/valid? ::tr/alkuosa aosa)) (conj "Alkuosa puuttuu tai on virheellinen")
    (not (s/valid? ::tr/alkuetaisyys aet)) (conj "Alkuetäisyys puuttuu tai on virheellinen")
    (not (s/valid? ::tr/loppuosa losa)) (conj "Loppuosa puuttuu tai on virheellinen")
    (not (s/valid? ::tr/loppuetaisyys loppuet)) (conj "Loppuetäisyys puuttuu tai on virheellinen")
    (not (s/valid? ::paikkaus/urapaikkaus-massatyyppi massatyyppi)) (conj "Massatyyppi puuttuu tai on virheellinen")
    (not (s/valid? ::paikkaus/raekoko raekoko)) (conj "Raekoko puuttuu tai on virheellinen")
    (not (s/valid? ::paikkaus/urapaikkaus-kuulamylly kuulamylly)) (conj "Kuulamylly puuttuu tai on virheellinen")
    (not (s/valid? ::paikkaus/pinta-ala pinta-ala)) (conj "Pinta-alaa ei voitu laskea!")
    (and
      (s/valid? ::paikkaus/alkuaika alkuaika)
      (s/valid? ::paikkaus/alkuaika alkuaika)
      (not (pvm/jalkeen? loppuaika alkuaika))) (conj "Loppuaika on ennen aloitusaikaa!")))

(defn- kasittele-urem-excel [db urakka-id paikkauskohde-id {kayttaja-id :id} req]
  (let [workbook (xls/load-workbook-from-file (:path (bean (get-in req [:params "file" :tempfile]))))
        {paikkaukset :paikkaukset
         urem-kok-massamaara :urem-kok-massamaara
         excel-luku-virhe :virhe} (p-excel/erottele-uremit workbook)
        paikkauskohde (first (paikkaus-q/hae-paikkauskohteet db {::paikkaus/id paikkauskohde-id}))
        paikkauskohteen-tila-virhe
        (when (not= "tilattu" (::paikkaus/paikkauskohteen-tila paikkauskohde))
          (log/error (str "Yritettiin luoda kohteelle, jonka tila ei ole 'tilattu', toteumaa :: kohteen-id " paikkauskohde-id))
          "Paikkauskohteen täytyy olla tilattu, jotta sille voi tehdä toteumia")

        paikkaukset (map (fn [rivi]
                           (update rivi :paikkaus
                             (fn [paikkaus]
                               (-> (zipmap (partial paikkauksen-domain-avaimet) paikkaus)
                                 (muuta-arvot intattavat-avaimet #(when (number? %) (int %)))
                                 (muuta-arvot bigdecattavat-avaimet #(when (number? %) (bigdec %)))
                                 (yrita-tehda-sijainti db)
                                 (tee-pituus db)
                                 tee-tr-osoite
                                 tee-pinta-ala
                                 tee-tienkohta
                                 (assoc
                                   ::muokkaustiedot/luoja-id kayttaja-id
                                   ::muokkaustiedot/luotu (pvm/nyt)
                                   ::paikkaus/urakka-id urakka-id
                                   ::paikkaus/lahde "excel"
                                   ::paikkaus/paikkauskohde-id paikkauskohde-id
                                   ::paikkaus/tyomenetelma (::paikkaus/tyomenetelma paikkauskohde)
                                   ::paikkaus/ulkoinen-id 0)))))
                      paikkaukset)
        kaikki-pinta-ala-yhteensa (reduce +
                                          (map (fn [rivi]
                                                 (or (get-in rivi [:paikkaus ::paikkaus/pinta-ala])
                                                     0M))
                                               paikkaukset))
        paikkaukset-massatietoineen (map (fn [rivi]
                                           (update rivi :paikkaus
                                                   (fn [paikkaus]
                                                     (as-> paikkaus p
                                                           (assoc p ::paikkaus/massamaara
                                                                    (laske-massamaara paikkaus urem-kok-massamaara kaikki-pinta-ala-yhteensa))
                                                           (assoc p ::paikkaus/massamenekki (laske-massamenekki p))))))
                                         paikkaukset)
        paikkausten-validointivirheet (into {} (map (fn [{rivi :rivi paikkaus :paikkaus}]
                                                      (let [validointivirheet (validoi-urem-excel-paikkaus paikkaus)]
                                                        (when-not (empty? validointivirheet)
                                                          [rivi validointivirheet])))
                                                    paikkaukset-massatietoineen))
        virheet (merge {}
                  (when (and (not excel-luku-virhe)
                             (not (number? urem-kok-massamaara)))
                    {:urem-kokonaismassamaaravirhe "Kohteen kokonaismassamäärä puuttuu, täytä solu A4."})
                  (when (seq paikkausten-validointivirheet) {:paikkausten-validointivirheet paikkausten-validointivirheet})
                  (when paikkauskohteen-tila-virhe {:paikkauskohteen-tila-virhe paikkauskohteen-tila-virhe})
                  (when excel-luku-virhe {:excel-luku-virhe excel-luku-virhe}))
        tallennetut-paikkaukset (when (empty? virheet)
                                  (mapv
                                    #(paikkaus-q/tallenna-urem-paikkaus-excelista db (:paikkaus %))
                                    paikkaukset-massatietoineen))
        body (cheshire/encode (cond
                                tallennetut-paikkaukset
                                {:message "OK"}
                                (seq virheet)
                                {:virheet virheet}
                                :else
                                {:virheet [{:virhe "Excelistä ei löydetty päällystyksiä!"}]}))]

    (when (seq paikkausten-validointivirheet)
      (log/error (str "Yritettiin tuoda urapaikkauksia excelillä, mutta paikkauksissa on virheitä. Virheet:"
                   paikkausten-validointivirheet)))

    {:status (if tallennetut-paikkaukset 200 400)
     :headers {"Content-Type" "application/json; charset=UTF-8"}
     :body body}))

(defn vastaanota-urem-excel [db req]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-paikkaukset-paikkauskohteetkustannukset
    (:kayttaja req)
    (Integer/parseInt (get (:params req) "urakka-id")))
  (let [urakka-id (Integer/parseInt (get (:params req) "urakka-id"))
        paikkauskohde-id (Integer/parseInt (get (:params req) "paikkauskohde-id"))
        kayttaja (:kayttaja req)]
    ;; Tarkistetaan, että kutsussa on mukana urakka ja kayttaja
    (if (and
          (some? urakka-id)
          (some? kayttaja)
          (some? paikkauskohde-id))
      (jdbc/with-db-transaction
        [db db]
        (kasittele-urem-excel db urakka-id paikkauskohde-id kayttaja req))
      (throw+ {:type "Error"
               :virheet [{:koodi "ERROR" :viesti "Urakka, käyttäjä tai paikkauskohde puuttuu."}]}))))

;; Korjataan tuotannossa oleva virhetilanne, jossa pot?=true merkinnän saaneet paikkauskohteet
;; eivät ole saaneet ylläpitokohdetta. Joten varmistetaan, että kaikilla pot-raportoitavilla on olemassa
;; ylläpitokohde
(defn- hae-paikkauskohteet [db user tiedot]
  (let [paikkauskohteet (paikkaus-q/paikkauskohteet db user tiedot)
        ;; Alustetaan mahdollisuus merkitä paikkauskohteet likaiseksi
        oli-puuttuva-yllapitokohde (atom false)
        _ (doall (for [kohde paikkauskohteet]
                   (let [uusi-kohde (when (and (:pot? kohde) (nil? (:yllapitokohde-id kohde)))
                                      ;; Löydettiin paikkauskohde, joka on pot raportoitava, mutta sille ei ole tehty ylläpitokohdetta
                                      (reset! oli-puuttuva-yllapitokohde true)
                                      (tarkista-pot-raportointi db kohde
                                                                ;; Feikataan kohteesta sellainen, että ikäänkuin pot raportointi olisi
                                                                ;; juuri nyt lyöty päälle
                                                                (assoc kohde :pot? false) (:id user)))
                         ;; Tallennetaan kohde uudestaan, koska se on saanut yllapitokohde-id:n
                         _ (when uusi-kohde
                             (tallenna-paikkauskohde! db nil nil user uusi-kohde false))])))]
    (if @oli-puuttuva-yllapitokohde
      ;; Jos puuttuvia tietoja oli, haetaan paikkauskohteet uudestaan
      (paikkaus-q/paikkauskohteet db user tiedot)
      ;; Normitilanteessa palautetaan jo löydetyt kohteet
      paikkauskohteet)))

(defrecord Paikkauskohteet [kehitysmoodi?]
  component/Lifecycle
  (start [this]
    (let [http (:http-palvelin this)
          fim (:fim this)
          email (:api-sahkoposti this)
          db (:db this)
          excel (:excel-vienti this)]
      (julkaise-palvelu http :paikkauskohteet-urakalle
                        (fn [user tiedot]
                          (hae-paikkauskohteet db user tiedot)))
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
      (julkaise-palvelu http :hae-paikkauskohteiden-tyomenetelmat
                        (fn [user tiedot]
                          (paikkaus-q/hae-paikkauskohteiden-tyomenetelmat db user tiedot)))
      (julkaise-palvelu http :lue-urapaikkaukset-excelista
        (wrap-multipart-params (fn [req] (vastaanota-urem-excel db req)))
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
      :tallenna-kasinsyotetty-paikkaus
      :poista-kasinsyotetty-paikkaus
      :hae-paikkauskohteen-tiemerkintaurakat
      :hae-paikkauskohteiden-tyomenetelmat
      :lue-urapaikkaukset-excelista
      (when (:excel-vienti this)
        (excel-vienti/poista-excel-kasittelija! (:excel-vienti this) :paikkauskohteet-urakalle-excel)))
    this))
