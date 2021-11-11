(ns harja.palvelin.palvelut.kulut.valikatselmukset
  (:require
    [com.stuartsierra.component :as component]
    [slingshot.slingshot :refer [throw+ try+]]
    [taoensso.timbre :as log]
    [specql.core :refer [columns]]
    [harja.domain.kulut.valikatselmus :as valikatselmus]
    [harja.domain.muokkaustiedot :as muokkaustiedot]
    [harja.domain.oikeudet :as oikeudet]
    [harja.domain.urakka :as urakka]
    [harja.kyselyt.urakat :as q-urakat]
    [harja.kyselyt.valikatselmus :as valikatselmus-q]
    [harja.kyselyt.urakat :as urakat-q]
    [harja.palvelin.palvelut.lupaus.lupaus-palvelu :as lupaus-palvelu]
    [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
    [harja.pvm :as pvm]
    [harja.domain.roolit :as roolit]
    [harja.domain.lupaus-domain :as lupaus-domain]
    [clojure.java.jdbc :as jdbc]))

;; Ensimmäinen veikkaus siitä, milloin tavoitehinnan oikaisuja saa tehdä.
;; Tarkentuu myöhemmin. Huomaa, että kuukaudet menevät 0-11.
(defn oikaisujen-sallittu-aikavali
  "Rakennetaan sallittu aikaväli valitun hoitokauden perusteella"
  [valittu-hoitokausi]
  (let [hoitokauden-alkuvuosi (pvm/vuosi (first valittu-hoitokausi))
        sallittu-viimeinen-vuosi (pvm/vuosi (second valittu-hoitokausi))]
    ;; Kuukausi-indexit on pielessä käytetyssä funktiossa, niin tuo vaikuttaa hassulta
    {:alkupvm (pvm/luo-pvm hoitokauden-alkuvuosi 8 1)
     :loppupvm (pvm/luo-pvm sallittu-viimeinen-vuosi 11 31)}))

(defn sallitussa-aikavalissa?
  "Tarkistaa onko päätöksen tekohetki hoitokauden sisällä tai muutama kuukausi sen yli"
  [valittu-hoitokausi nykyhetki]
  (let [sallittu-aikavali (oikaisujen-sallittu-aikavali valittu-hoitokausi)]
    (pvm/valissa? nykyhetki (:alkupvm sallittu-aikavali) (:loppupvm sallittu-aikavali))))

(defn heita-virhe [viesti] (throw+ {:type "Error"
                                    :virheet {:koodi "ERROR" :viesti viesti}}))

(defn tarkista-aikavali
  "Tarkistaa, ollaanko kutsuhetkellä (nyt) tavoitehinnan oikaisujen tai päätösten teon sallitussa aikavälissä, eli
  Suomen aikavyöhykkeellä syyskuun 1. päivän ja joulukuun viimeisen päivän välissä valitun hoitokauden aikana. Muulloin heittää virheen."
  [urakka toimenpide kayttaja valittu-hoitokausi]
  (let [nykyhetki (pvm/nyt)
        toimenpide-teksti (case toimenpide
                            :paatos "Urakan päätöksiä"
                            :tavoitehinnan-oikaisu "Tavoitehinnan oikaisuja")
        urakka-aktiivinen? (pvm/valissa? (pvm/nyt) (:alkupvm urakka) (:loppupvm urakka))
        sallittu-aikavali (oikaisujen-sallittu-aikavali valittu-hoitokausi)
        sallitussa-aikavalissa? (sallitussa-aikavalissa? valittu-hoitokausi nykyhetki)
        jvh? (roolit/jvh? kayttaja)

        ;; MH urakoissa on pakko sallia muutokset vuosille 2019 ja 2020, koska päätöksiä ei ole voitu ennen vuoden 2021 syksyä
        ;; näille urakoille tehdä, johtuen päätösten myöhäisestä valmistumisesta. Niinpä sallitaan 2023 vuoteen asti näille muutokset
        ;; sallimalla aikavälitarkistus.
        viimeinen-poikkeusaika (pvm/->pvm "31.12.2022")
        poikkeusvuosi? (and
                         (pvm/sama-tai-ennen? (pvm/nyt) viimeinen-poikkeusaika)
                         (lupaus-domain/urakka-19-20? urakka))]
    (when-not (or jvh? urakka-aktiivinen? poikkeusvuosi?) (heita-virhe (str toimenpide-teksti " ei voi käsitellä urakka-ajan ulkopuolella")))
    (when-not (or jvh? sallitussa-aikavalissa? poikkeusvuosi?)
      (throw+ {:type "Error"
               :virheet {:koodi "ERROR" :viesti (str toimenpide-teksti " saa käsitellä ainoastaan sallitulla aikavälillä.")}}))))

(defn tarkista-valikatselmusten-urakkatyyppi [urakka toimenpide]
  (let [toimenpide-teksti (case toimenpide
                            :paatos "Urakan päätöksiä"
                            :tavoitehinnan-oikaisu "Tavoitehinnan oikaisuja"
                            :kattohinnan-oikaisu "Kattohinnan oikaisuja")]
    (when-not (= "teiden-hoito" (:tyyppi urakka))
      (throw+ {:type "Error"
               :virheet {:koodi "ERROR" :viesti (str toimenpide-teksti " saa tehdä ainoastaan teiden hoitourakoille")}}))))

(defn tarkista-ei-siirtoa-viimeisena-vuotena [tiedot urakka]
  (let [siirto (::valikatselmus/siirto tiedot)
        siirto? (and (some? siirto)
                     (pos? siirto))
        viimeinen-vuosi? (= (pvm/vuosi (:loppupvm urakka)) (pvm/vuosi (pvm/nyt)))]
    (when (and siirto? viimeinen-vuosi?) (heita-virhe "Kattohinnan ylitystä ei voi siirtää ensi vuodelle urakan viimeisenä vuotena"))))

(defn tarkista-ei-siirtoa-tavoitehinnan-ylityksessa [tiedot]
  (let [siirto (::valikatselmus/siirto tiedot)
        siirto? (and (some? siirto)
                     (< 0 siirto))]
    (when siirto? (heita-virhe "Tavoitehinnan ylitystä ei voi siirtää ensi vuodelle"))))

(defn tarkista-maksun-miinusmerkki-alituksessa [tiedot]
  (let [urakoitsijan-maksu (or (::valikatselmus/urakoitsijan-maksu tiedot) 0)]
    (when (pos? urakoitsijan-maksu)
      (heita-virhe "Tavoitehinnan alituksessa urakoitsijan maksun täytyy olla miinusmerkkinen tai nolla"))))

(defn tarkista-tavoitehinnan-ylitys [{::valikatselmus/keys [tilaajan-maksu urakoitsijan-maksu] :as tiedot} tavoitehinta kattohinta]
  (let [;; ylitys ei saa ylittää tavoitehinnan tiettyä osaa
        _ (when-not (and (number? tavoitehinta) (number? tilaajan-maksu) (number? urakoitsijan-maksu))
            (heita-virhe "Tavoitehinnan ylityspäätös vaatii tavoitehinnan, tilaajan-maksun ja urajoitsijan-maksun."))
        ylityksen-maksimimaara (- kattohinta tavoitehinta)]
    (do
      ;; Urakoitsijan maksut ja tilaajan maksut eivät saa ylittää yli 10% tavoitehinnasta, koska muuten maksetaan jo kattohinnan ylityksiä
      (when (> (+ tilaajan-maksu urakoitsijan-maksu) ylityksen-maksimimaara)
        (heita-virhe "Maksujen osuus suurempi, kuin tavoitehinnan ja kattohinnan erotus."))

      ;; Tarkista siirto
      (tarkista-ei-siirtoa-tavoitehinnan-ylityksessa tiedot))))

(defn tarkista-lupaus-bonus
  "Varmista, että annettu bonus täsmää lupauksista saatavaan bonukseen"
  [db kayttaja tiedot]
  (let [urakan-tiedot (first (urakat-q/hae-urakka db {:id (::urakka/id tiedot)}))
        urakan-alkuvuosi (pvm/vuosi (:alkupvm urakan-tiedot))
        hakuparametrit {:urakka-id (::urakka/id tiedot)
                        :urakan-alkuvuosi urakan-alkuvuosi
                        :nykyhetki (pvm/nyt)
                        :valittu-hoitokausi [(pvm/luo-pvm (::valikatselmus/hoitokauden-alkuvuosi tiedot) 9 1)
                                             (pvm/luo-pvm (inc (::valikatselmus/hoitokauden-alkuvuosi tiedot)) 8 30)]}
        vanha-mhu? (lupaus-domain/urakka-19-20? urakan-tiedot)
        lupaustiedot (if vanha-mhu?
                       (lupaus-palvelu/hae-kuukausittaiset-pisteet-hoitokaudelle db kayttaja hakuparametrit)
                       (lupaus-palvelu/hae-urakan-lupaustiedot-hoitokaudelle db hakuparametrit))
        tilaajan-maksu (bigdec (::valikatselmus/tilaajan-maksu tiedot))
        laskettu-bonus (bigdec (or (get-in lupaustiedot [:yhteenveto :bonus-tai-sanktio :bonus]) 0M))]
    (cond (and
            ;; Varmistetaan, että tyyppi täsmää
            (= (::valikatselmus/tyyppi tiedot) ::valikatselmus/lupaus-bonus)
            ;; Tarkistetaan, että bonus on annettu, jotta voidaan tarkistaa luvut
            (get-in lupaustiedot [:yhteenveto :bonus-tai-sanktio :bonus])
            ;; Varmistetaan, että lupauksissa laskettu bonus täsmää päätöksen bonukseen
            (= laskettu-bonus tilaajan-maksu))
          true
          (and
            ;; Varmistetaan, että tyyppi täsmää
            (= (::valikatselmus/tyyppi tiedot) ::valikatselmus/lupaus-bonus)
            ;; Tarkistetaan, että tavoite on täytetty, eli nolla case, jotta voidaan tarkistaa luvut
            (get-in lupaustiedot [:yhteenveto :bonus-tai-sanktio :tavoite-taytetty])
            ;; Varmistetaan, että lupauksissa laskettu sanktio täsmää päätöksen sanktioon
            (= (bigdec 0) tilaajan-maksu))
          true
          :else
          (do
            (log/warn "Lupausbonuksen tilaajan maksun summa ei täsmää lupauksissa lasketun bonuksen kanssa.
            Laskettu bonus: " laskettu-bonus " tilaajan maksu: " tilaajan-maksu)
            (heita-virhe "Lupausbonuksen tilaajan maksun summa ei täsmää lupauksissa lasketun bonuksen kanssa.")))))

(defn tarkista-lupaus-sanktio
  "Varmista, että tuleva sanktio täsmää lupauksista saatavaan sanktioon"
  [db kayttaja tiedot]
  (let [urakan-tiedot (first (urakat-q/hae-urakka db {:id (::urakka/id tiedot)}))
        urakan-alkuvuosi (pvm/vuosi (:alkupvm urakan-tiedot))
        hakuparametrit {:urakka-id (::urakka/id tiedot)
                        :urakan-alkuvuosi urakan-alkuvuosi
                        :nykyhetki (pvm/nyt)
                        :valittu-hoitokausi [(pvm/luo-pvm (::valikatselmus/hoitokauden-alkuvuosi tiedot) 9 1)
                                             (pvm/luo-pvm (inc (::valikatselmus/hoitokauden-alkuvuosi tiedot)) 8 30)]}
        ;; Lupauksia käsitellään täysin eri tavalla riippuen urakan alkuvuodesta
        lupaukset (if (lupaus-domain/vuosi-19-20? urakan-alkuvuosi)
                    (lupaus-palvelu/hae-kuukausittaiset-pisteet-hoitokaudelle db kayttaja hakuparametrit)
                    (lupaus-palvelu/hae-urakan-lupaustiedot-hoitokaudelle db hakuparametrit))]
    (cond (and
            ;; Varmistetaan, että tyyppi täsmää
            (= (::valikatselmus/tyyppi tiedot) ::valikatselmus/lupaus-sanktio)
            ;; Tarkistetaan, että sanktio on annettu, jotta voidaan tarkistaa luvut
            (get-in lupaukset [:yhteenveto :bonus-tai-sanktio :sanktio])
            ;; Varmistetaan, että lupauksissa laskettu sanktio täsmää päätöksen sanktioon
            (= (bigdec (get-in lupaukset [:yhteenveto :bonus-tai-sanktio :sanktio])) (bigdec (::valikatselmus/urakoitsijan-maksu tiedot))))
          true
          (and
            ;; Varmistetaan, että tyyppi täsmää
            (= (::valikatselmus/tyyppi tiedot) ::valikatselmus/lupaus-sanktio)
            ;; Tarkistetaan, että tavoite on täytetty, eli nolla case, jotta voidaan tarkistaa luvut
            (get-in lupaukset [:yhteenveto :bonus-tai-sanktio :tavoite-taytetty])
            ;; Varmistetaan, että lupauksissa laskettu sanktio täsmää päätöksen sanktioon
            (= (bigdec 0) (bigdec (::valikatselmus/urakoitsijan-maksu tiedot))))
          true
          :else
          (heita-virhe "Lupaussanktion urakoitsijan maksun summa ei täsmää lupauksissa lasketun sanktion kanssa."))))

(defn tarkista-kattohinnan-ylitys [tiedot urakka]
  (do
    (tarkista-ei-siirtoa-viimeisena-vuotena tiedot urakka)))

(defn tarkista-maksun-maara-alituksessa [db tiedot urakka tavoitehinta hoitokauden-alkuvuosi]
  (let [maksu (- (::valikatselmus/urakoitsijan-maksu tiedot))
        viimeinen-hoitokausi? (= (pvm/vuosi (:loppupvm urakka)) (inc hoitokauden-alkuvuosi))
        maksimi-tavoitepalkkio (* valikatselmus/+maksimi-tavoitepalkkio-prosentti+ tavoitehinta)]
    (when (and (not viimeinen-hoitokausi?) (> maksu maksimi-tavoitepalkkio))
      (heita-virhe "Urakoitsijalle maksettava summa ei saa ylittää 3% tavoitehinnasta"))))

(defn tarkista-tavoitehinnan-alitus [db tiedot urakka tavoitehinta hoitokauden-alkuvuosi]
  (do
    (tarkista-maksun-miinusmerkki-alituksessa tiedot)
    (tarkista-maksun-maara-alituksessa db tiedot urakka tavoitehinta hoitokauden-alkuvuosi)))

(defn alkuvuosi->hoitokausi [urakka hoitokauden-alkuvuosi]
  (let [urakan-aloitusvuosi (pvm/vuosi (:alkupvm urakka))]
    (inc (- hoitokauden-alkuvuosi urakan-aloitusvuosi))))

;; Tavoitehinnan oikaisuja tehdään loppuvuodesta välikatselmuksessa.
;; Nämä summataan tai vähennetään alkuperäisestä tavoitehinnasta.
(defn tallenna-tavoitehinnan-oikaisu [db kayttaja tiedot]
  (log/debug "tallenna-tavoitehinnan-oikaisu :: tiedot" (pr-str tiedot))
  (let [urakka-id (::urakka/id tiedot)
        urakka (first (q-urakat/hae-urakka db urakka-id))
        hoitokauden-alkuvuosi (::valikatselmus/hoitokauden-alkuvuosi tiedot)
        ;; Rakennetaan valittu hoitokausi
        valittu-hoitokausi [(pvm/hoitokauden-alkupvm hoitokauden-alkuvuosi)
                            (pvm/hoitokauden-loppupvm (inc hoitokauden-alkuvuosi))]
        _ (do (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu
                                              kayttaja
                                              urakka-id)
              (tarkista-valikatselmusten-urakkatyyppi urakka :tavoitehinnan-oikaisu)
              (tarkista-aikavali urakka :tavoitehinnan-oikaisu kayttaja valittu-hoitokausi))
        tiedot (select-keys tiedot (columns ::valikatselmus/tavoitehinnan-oikaisu))
        oikaisu-specql (merge tiedot {::urakka/id urakka-id
                                      ::muokkaustiedot/luoja-id (:id kayttaja)
                                      ::muokkaustiedot/muokkaaja-id (:id kayttaja)
                                      ::muokkaustiedot/luotu (or (::muokkaustiedot/luotu tiedot) (pvm/nyt))
                                      ::muokkaustiedot/muokattu (or (::muokkaustiedot/muokattu tiedot) (pvm/nyt))
                                      ::valikatselmus/summa (bigdec (::valikatselmus/summa tiedot))
                                      ::valikatselmus/hoitokauden-alkuvuosi hoitokauden-alkuvuosi})]
    (valikatselmus-q/poista-paatokset db hoitokauden-alkuvuosi)
    (if (::valikatselmus/oikaisun-id tiedot)
      (valikatselmus-q/paivita-oikaisu db oikaisu-specql)
      (valikatselmus-q/tee-oikaisu db oikaisu-specql))))

(defn poista-tavoitehinnan-oikaisu [db kayttaja {::valikatselmus/keys [oikaisun-id] :as tiedot}]
  {:pre [(number? oikaisun-id)]}
  (log/debug "poista-tavoitehinnan-oikaisu :: tiedot" (pr-str tiedot))
  (let [oikaisu (valikatselmus-q/hae-oikaisu db oikaisun-id)
        hoitokauden-alkuvuosi (::valikatselmus/hoitokauden-alkuvuosi oikaisu)
        urakka-id (::urakka/id oikaisu)
        urakka (first (q-urakat/hae-urakka db urakka-id))
        ;; Rakennetaan valittu hoitokausi
        valittu-hoitokausi [(pvm/hoitokauden-alkupvm hoitokauden-alkuvuosi)
                            (pvm/hoitokauden-loppupvm (inc hoitokauden-alkuvuosi))]
        _ (do (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu
                                              kayttaja
                                              urakka-id)
              (tarkista-valikatselmusten-urakkatyyppi urakka :tavoitehinnan-oikaisu)
              (tarkista-aikavali urakka :tavoitehinnan-oikaisu kayttaja valittu-hoitokausi))]
    (valikatselmus-q/poista-paatokset db hoitokauden-alkuvuosi)
    (valikatselmus-q/poista-oikaisu db tiedot)))

(defn hae-tavoitehintojen-oikaisut [db _kayttaja tiedot]
  (let [urakka-id (::urakka/id tiedot)]
    (assert (number? urakka-id) "Virhe urakan ID:ssä.")
    (valikatselmus-q/hae-oikaisut db tiedot)))

;; Kattohinnan oikaisuja tehdään loppuvuodesta välikatselmuksessa 2019-2020 alkaneille urakoille.
;; Asetetaan uusi arvo kattohinnalle.
(defn tallenna-kattohinnan-oikaisu
  [db kayttaja {urakka-id ::urakka/id
                hoitokauden-alkuvuosi ::valikatselmus/hoitokauden-alkuvuosi
                uusi-kattohinta ::valikatselmus/uusi-kattohinta
                :as tiedot}]
  (log/debug "tallenna-kattohinnan-oikaisu :: tiedot" (pr-str tiedot))
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu
    kayttaja
    urakka-id)
  (jdbc/with-db-transaction [db db]
    (let [urakka (first (q-urakat/hae-urakka db urakka-id))
          ;; Rakennetaan valittu hoitokausi
          valittu-hoitokausi [(pvm/hoitokauden-alkupvm hoitokauden-alkuvuosi)
                              (pvm/hoitokauden-loppupvm (inc hoitokauden-alkuvuosi))]
          _ (do
              (tarkista-valikatselmusten-urakkatyyppi urakka :kattohinnan-oikaisu)
              (tarkista-aikavali urakka :tavoitehinnan-oikaisu kayttaja valittu-hoitokausi))
          vanha-rivi (valikatselmus-q/hae-kattohinnan-oikaisu db urakka-id hoitokauden-alkuvuosi)
          oikaisu-specql (merge
                           vanha-rivi
                           {::urakka/id urakka-id
                            ::muokkaustiedot/poistettu? false ; Rivi voi olla poistettu aikaisemmin
                            ::muokkaustiedot/luoja-id (:id kayttaja)
                            ::muokkaustiedot/muokkaaja-id (:id kayttaja)
                            ::muokkaustiedot/luotu (pvm/nyt)
                            ::muokkaustiedot/muokattu (pvm/nyt)
                            ::valikatselmus/uusi-kattohinta (bigdec uusi-kattohinta)
                            ::valikatselmus/hoitokauden-alkuvuosi hoitokauden-alkuvuosi})]
      (valikatselmus-q/poista-paatokset db hoitokauden-alkuvuosi)
      (if (::valikatselmus/kattohinnan-oikaisun-id oikaisu-specql)
        (do (valikatselmus-q/paivita-kattohinnan-oikaisu db oikaisu-specql)
            (valikatselmus-q/hae-kattohinnan-oikaisu db urakka-id hoitokauden-alkuvuosi))
        (valikatselmus-q/tee-kattohinnan-oikaisu db oikaisu-specql)))))

(defn poista-kattohinnan-oikaisu [db kayttaja {hoitokauden-alkuvuosi ::valikatselmus/hoitokauden-alkuvuosi urakka-id ::urakka/id :as tiedot}]
  {:pre [(number? urakka-id) (number? hoitokauden-alkuvuosi)]}
  (log/debug "poista-kattohinnan-oikaisu :: tiedot" (pr-str tiedot))
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu
    kayttaja
    urakka-id)
  (jdbc/with-db-transaction [db db]
    (let [urakka (first (q-urakat/hae-urakka db urakka-id))
          ;; Rakennetaan valittu hoitokausi
          valittu-hoitokausi [(pvm/hoitokauden-alkupvm hoitokauden-alkuvuosi)
                              (pvm/hoitokauden-loppupvm (inc hoitokauden-alkuvuosi))]
          _ (do
              (tarkista-valikatselmusten-urakkatyyppi urakka :tavoitehinnan-oikaisu)
              (tarkista-aikavali urakka :tavoitehinnan-oikaisu kayttaja valittu-hoitokausi))]
      (valikatselmus-q/poista-paatokset db hoitokauden-alkuvuosi)
      (valikatselmus-q/poista-kattohinnan-oikaisu db urakka-id hoitokauden-alkuvuosi kayttaja)
      (valikatselmus-q/hae-kattohinnan-oikaisu db urakka-id hoitokauden-alkuvuosi))))

(defn hae-kattohintojen-oikaisut [db _kayttaja tiedot]
  (let [urakka-id (::urakka/id tiedot)]
    (assert (number? urakka-id) "Virhe urakan ID:ssä.")
    (valikatselmus-q/hae-kattohinnan-oikaisut db tiedot)))

(defn tee-paatoksen-tiedot [tiedot kayttaja hoitokauden-alkuvuosi]
  (merge tiedot {::valikatselmus/tyyppi (name (::valikatselmus/tyyppi tiedot))
                 ::valikatselmus/hoitokauden-alkuvuosi hoitokauden-alkuvuosi
                 ::valikatselmus/siirto (bigdec (or (::valikatselmus/siirto tiedot) 0))
                 ::valikatselmus/urakoitsijan-maksu (bigdec (or (::valikatselmus/urakoitsijan-maksu tiedot) 0))
                 ::valikatselmus/tilaajan-maksu (bigdec (or (::valikatselmus/tilaajan-maksu tiedot) 0))
                 ::muokkaustiedot/poistettu? false
                 ::muokkaustiedot/luoja-id (:id kayttaja)
                 ::muokkaustiedot/muokkaaja-id (:id kayttaja)
                 ::muokkaustiedot/luotu (or (::muokkaustiedot/luotu tiedot) (pvm/nyt))
                 ::muokkaustiedot/muokattu (or (::muokkaustiedot/muokattu tiedot (pvm/nyt)))}))

(defn hae-urakan-paatokset [db kayttaja tiedot]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu
                             kayttaja
                             (::urakka/id tiedot))
  (valikatselmus-q/hae-urakan-paatokset db tiedot))

(defn tee-paatos-urakalle [db kayttaja tiedot]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu
                                  kayttaja
                                  (::urakka/id tiedot))
  (log/debug "tee-paatos-urakalle :: tiedot" (pr-str tiedot))
  (let [urakka-id (::urakka/id tiedot)
        urakka (first (q-urakat/hae-urakka db urakka-id))
        hoitokauden-alkuvuosi (::valikatselmus/hoitokauden-alkuvuosi tiedot)
        ;; Rakennetaan valittu hoitokausi
        valittu-hoitokausi [(pvm/hoitokauden-alkupvm hoitokauden-alkuvuosi)
                            (pvm/hoitokauden-loppupvm (inc hoitokauden-alkuvuosi))]
        _ (do
            (tarkista-valikatselmusten-urakkatyyppi urakka :paatos)
            (tarkista-aikavali urakka :paatos kayttaja valittu-hoitokausi))
        paatoksen-tyyppi (::valikatselmus/tyyppi tiedot)
        tavoitehinta (valikatselmus-q/hae-oikaistu-tavoitehinta db {:urakka-id urakka-id
                                                                    :hoitokauden-alkuvuosi hoitokauden-alkuvuosi})
        kattohinta (valikatselmus-q/hae-oikaistu-kattohinta db {:urakka-id urakka-id
                                                                :hoitokauden-alkuvuosi hoitokauden-alkuvuosi})]
    (case paatoksen-tyyppi
      ::valikatselmus/tavoitehinnan-ylitys (tarkista-tavoitehinnan-ylitys tiedot tavoitehinta kattohinta)
      ::valikatselmus/kattohinnan-ylitys (tarkista-kattohinnan-ylitys tiedot urakka)
      ::valikatselmus/tavoitehinnan-alitus (tarkista-tavoitehinnan-alitus db tiedot urakka tavoitehinta hoitokauden-alkuvuosi)
      ::valikatselmus/lupaus-bonus (tarkista-lupaus-bonus db kayttaja tiedot)
      ::valikatselmus/lupaus-sanktio (tarkista-lupaus-sanktio db kayttaja tiedot))
    (valikatselmus-q/tee-paatos db (tee-paatoksen-tiedot tiedot kayttaja hoitokauden-alkuvuosi))))

(defn poista-lupaus-paatos
  "Muissa päätöstyypeissä muokkaaminen tarkoittaa lähinnä summien muokkausta. Itse päätöstä ei poisteta.
  Lupausten kohdalla liian aikaisin tehty päätös lukitsee lupausten muokkaamisen, joten lupauspäätöksen muokkaus on
  itseasiassa vain päätöksen poistaminen, joka vapauttaa taas muokkausmahdollisuuden lupauksiin."
  [db kayttaja {:keys [paatos-id] :as tiedot}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu
                                  kayttaja
                                  (::urakka/id tiedot))
  (log/debug "poista-lupaus-paatos :: tiedot" (pr-str tiedot))
  (let [vastaus (if-not (number? paatos-id)
                  (heita-virhe "Lupauspäätöksen id puuttuu!")
                  (valikatselmus-q/poista-lupaus-paatos db paatos-id))]
    vastaus))

(defrecord Valikatselmukset []
  component/Lifecycle
  (start [this]
    (let [http (:http-palvelin this)
          db (:db this)]
      (julkaise-palvelu http :tallenna-tavoitehinnan-oikaisu
                        (fn [user tiedot]
                          (tallenna-tavoitehinnan-oikaisu db user tiedot)))
      (julkaise-palvelu http :hae-tavoitehintojen-oikaisut
                        (fn [user tiedot]
                          (hae-tavoitehintojen-oikaisut db user tiedot)))
      (julkaise-palvelu http :poista-tavoitehinnan-oikaisu
                        (fn [user tiedot]
                          (poista-tavoitehinnan-oikaisu db user tiedot)))
      (julkaise-palvelu http :tallenna-kattohinnan-oikaisu
        (fn [user tiedot]
          (tallenna-kattohinnan-oikaisu db user tiedot)))
      (julkaise-palvelu http :hae-kattohintojen-oikaisut
        (fn [user tiedot]
          (hae-kattohintojen-oikaisut db user tiedot)))
      (julkaise-palvelu http :poista-kattohinnan-oikaisu
        (fn [user tiedot]
          (poista-kattohinnan-oikaisu db user tiedot)))
      (julkaise-palvelu http :hae-urakan-paatokset
                        (fn [user tiedot]
                          (hae-urakan-paatokset db user tiedot)))
      (julkaise-palvelu http :tallenna-urakan-paatos
                        (fn [user tiedot]
                          (tee-paatos-urakalle db user tiedot)))
      (julkaise-palvelu http :poista-lupaus-paatos
                        (fn [user tiedot]
                          (poista-lupaus-paatos db user tiedot)))
      this))
  (stop [this]
    (poista-palvelut (:http-palvelin this)
                     :tallenna-tavoitehinnan-oikaisu
                     :hae-tavoitehintojen-oikaisut
                     :poista-tavoitehinnan-oikaisu
                     :tallenna-kattohinnan-oikaisu
                     :hae-kattohintojen-oikaisut
                     :poista-kattohinnan-oikaisu
                     :tallenna-urakan-paatos
                     :poista-lupaus-paatos)
    this))