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
    [harja.kyselyt.budjettisuunnittelu :as budjettisuunnittelu-q]
    [harja.palvelin.palvelut.laadunseuranta :as laadunseuranta-palvelu]
    [harja.palvelin.palvelut.toteumat :as toteumat-palvelu]
    [harja.palvelin.palvelut.kulut :as kulut-palvelu]
    [harja.palvelin.palvelut.kulut.paatos-apurit :as paatos-apurit]
    [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
    [harja.pvm :as pvm]
    [harja.domain.roolit :as roolit]
    [harja.domain.lupaus-domain :as lupaus-domain]
    [clojure.java.jdbc :as jdbc]
    [harja.tyokalut.yleiset :refer [round2]]))

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
        ;; Pyöristetään, koska tilaajan-maksu ja urakoitsijan-maksu tulevat frontilta liukulukuina, joten laskuissa voi tulla virhettä
        ;; Esim.
        ;; (+ 18970.6678707 44264.8916983) ;; => 63235.559569000005
        ylityksen-maksimimaara (round2 8 (- kattohinta tavoitehinta))
        maksujen-osuus (round2 8 (+ tilaajan-maksu urakoitsijan-maksu))]
    (do
      ;; Urakoitsijan maksut ja tilaajan maksut eivät saa ylittää yli 10% tavoitehinnasta, koska muuten maksetaan jo kattohinnan ylityksiä
      (when (> maksujen-osuus ylityksen-maksimimaara)
        (heita-virhe "Maksujen osuus suurempi, kuin tavoitehinnan ja kattohinnan erotus."))

      ;; Tarkista siirto
      (tarkista-ei-siirtoa-tavoitehinnan-ylityksessa tiedot))))

(defn- poista-urakan-paatokset [db urakka-id hoitokauden-alkuvuosi kayttaja]
  (let [paatokset (valikatselmus-q/hae-urakan-paatokset-hoitovuodelle db urakka-id hoitokauden-alkuvuosi)]
    (doseq [paatos paatokset]
      (cond
        ;; Poista lupaussanktio myös
        (and
          (= (::valikatselmus/tyyppi paatos) "lupaussanktio")
          (not (nil? (::valikatselmus/sanktio-id paatos))))
        (laadunseuranta-palvelu/poista-suorasanktio db kayttaja {:id (::valikatselmus/sanktio-id paatos) :urakka-id urakka-id})
        ;; Poista lupausbonus myöskin
        (and
          (= (::valikatselmus/tyyppi paatos) "lupausbonus")
          (not (nil? (::valikatselmus/erilliskustannus-id paatos))))
        (toteumat-palvelu/poista-erilliskustannus db kayttaja
          {:id (::valikatselmus/erilliskustannus-id paatos) :urakka-id urakka-id})
        ;; Poista päätöksen kulut
        (and
          (or (= (::valikatselmus/tyyppi paatos) "tavoitehinnan-ylitys")
            (= (::valikatselmus/tyyppi paatos) "kattohinnan-ylitys")
            (= (::valikatselmus/tyyppi paatos) "tavoitehinnan-alitus"))
          (not (nil? (::valikatselmus/kulu-id paatos))))
        (kulut-palvelu/poista-kulu-tietokannasta db kayttaja {:urakka-id urakka-id :id (::valikatselmus/kulu-id paatos)})))
    (valikatselmus-q/poista-paatokset db urakka-id hoitokauden-alkuvuosi (:id kayttaja))))

(defn tarkista-kattohinnan-ylitys [tiedot urakka]
  (do
    (tarkista-ei-siirtoa-viimeisena-vuotena tiedot urakka)))

(defn tarkista-maksun-maara-alituksessa [tiedot urakka tavoitehinta hoitokauden-alkuvuosi]
  (let [maksu (- (::valikatselmus/urakoitsijan-maksu tiedot))
        viimeinen-hoitokausi? (= (pvm/vuosi (:loppupvm urakka)) (inc hoitokauden-alkuvuosi))
        maksimi-tavoitepalkkio (* valikatselmus/+maksimi-tavoitepalkkio-prosentti+ tavoitehinta)]
    (when (and (not viimeinen-hoitokausi?) (> maksu maksimi-tavoitepalkkio))
      (heita-virhe "Urakoitsijalle maksettava summa ei saa ylittää 3% tavoitehinnasta"))))

(defn tarkista-tavoitehinnan-alitus [tiedot urakka tavoitehinta hoitokauden-alkuvuosi]
  (do
    (tarkista-maksun-miinusmerkki-alituksessa tiedot)
    (tarkista-maksun-maara-alituksessa tiedot urakka tavoitehinta hoitokauden-alkuvuosi)))

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
              #_ (tarkista-aikavali urakka :tavoitehinnan-oikaisu kayttaja valittu-hoitokausi))
        tiedot (select-keys tiedot (columns ::valikatselmus/tavoitehinnan-oikaisu))
        oikaisu-specql (merge tiedot {::urakka/id urakka-id
                                      ::muokkaustiedot/luoja-id (:id kayttaja)
                                      ::muokkaustiedot/muokkaaja-id (:id kayttaja)
                                      ::muokkaustiedot/luotu (or (::muokkaustiedot/luotu tiedot) (pvm/nyt))
                                      ::muokkaustiedot/muokattu (pvm/nyt)
                                      ::valikatselmus/summa (bigdec (::valikatselmus/summa tiedot))
                                      ::valikatselmus/hoitokauden-alkuvuosi hoitokauden-alkuvuosi})]
    (poista-urakan-paatokset db urakka-id hoitokauden-alkuvuosi kayttaja)
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
              #_ (tarkista-aikavali urakka :tavoitehinnan-oikaisu kayttaja valittu-hoitokausi))]
    (poista-urakan-paatokset db urakka-id hoitokauden-alkuvuosi kayttaja)
    (valikatselmus-q/poista-oikaisu db tiedot)))

(defn hae-tavoitehintojen-oikaisut [db kayttaja tiedot]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu kayttaja (::urakka/id tiedot))
  (let [urakka-id (::urakka/id tiedot)]
    (assert (number? urakka-id) "Virhe urakan ID:ssä.")
    (valikatselmus-q/hae-oikaisut db tiedot)))

(defn oikaistu-tavoitehinta-vuodelle [db urakka-id hoitokauden-alkuvuosi]
  (:tavoitehinta-oikaistu
    (budjettisuunnittelu-q/budjettitavoite-vuodelle db urakka-id hoitokauden-alkuvuosi)))

(defn tarkista-kattohinta-suurempi-kuin-tavoitehinta [db urakka-id hoitokauden-alkuvuosi uusi-kattohinta]
  (let [oikaistu-tavoitehinta (oikaistu-tavoitehinta-vuodelle db urakka-id hoitokauden-alkuvuosi)]
    (when-not oikaistu-tavoitehinta
      (throw+ {:type "Error"
               :virheet {:koodi "ERROR" :viesti "Oikaistua tavoitehintaa ei ole saatavilla, joten uutta kattohintaa ei voida asettaa"}}))
    (when-not (>= uusi-kattohinta oikaistu-tavoitehinta)
      (throw+ {:type "Error"
               :virheet {:koodi "ERROR" :viesti "Kattohinnan täytyy olla suurempi kuin tavoitehinta"}}))))

;; Kattohinnan oikaisuja tehdään loppuvuodesta välikatselmuksessa 2019-2020 alkaneille urakoille.
;; Asetetaan uusi arvo kattohinnalle.
(defn tallenna-kattohinnan-oikaisu
  [db kayttaja {urakka-id ::urakka/id
                hoitokauden-alkuvuosi ::valikatselmus/hoitokauden-alkuvuosi
                uusi-kattohinta ::valikatselmus/uusi-kattohinta
                :as tiedot}]
  {:pre [(number? urakka-id) (pos-int? hoitokauden-alkuvuosi) (number? uusi-kattohinta) (pos? uusi-kattohinta)]}
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
              #_ (tarkista-aikavali urakka :tavoitehinnan-oikaisu kayttaja valittu-hoitokausi)
              (tarkista-kattohinta-suurempi-kuin-tavoitehinta db urakka-id hoitokauden-alkuvuosi uusi-kattohinta))
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
      (poista-urakan-paatokset db urakka-id hoitokauden-alkuvuosi kayttaja)

      (if (::valikatselmus/kattohinnan-oikaisun-id oikaisu-specql)
        (do (valikatselmus-q/paivita-kattohinnan-oikaisu db oikaisu-specql)
            (valikatselmus-q/hae-kattohinnan-oikaisu db urakka-id hoitokauden-alkuvuosi))
        (valikatselmus-q/tee-kattohinnan-oikaisu db oikaisu-specql)))))

(defn poista-kattohinnan-oikaisu [db kayttaja {hoitokauden-alkuvuosi ::valikatselmus/hoitokauden-alkuvuosi urakka-id ::urakka/id :as tiedot}]
  {:pre [(number? urakka-id) (pos-int? hoitokauden-alkuvuosi)]}
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
              #_ (tarkista-aikavali urakka :tavoitehinnan-oikaisu kayttaja valittu-hoitokausi))]
      (poista-urakan-paatokset db urakka-id hoitokauden-alkuvuosi kayttaja)
      (valikatselmus-q/poista-kattohinnan-oikaisu db urakka-id hoitokauden-alkuvuosi kayttaja)
      (valikatselmus-q/hae-kattohinnan-oikaisu db urakka-id hoitokauden-alkuvuosi))))

(defn hae-kattohintojen-oikaisut [db _kayttaja tiedot]
  (let [urakka-id (::urakka/id tiedot)]
    (assert (number? urakka-id) "Virhe urakan ID:ssä.")
    (valikatselmus-q/hae-kattohinnan-oikaisut db tiedot)))

(defn tee-paatoksen-tiedot [tiedot kayttaja hoitokauden-alkuvuosi erilliskustannus_id sanktio_id kulu_id]
  (merge tiedot {::valikatselmus/tyyppi (name (::valikatselmus/tyyppi tiedot))
                 ::valikatselmus/hoitokauden-alkuvuosi hoitokauden-alkuvuosi
                 ::valikatselmus/siirto (bigdec (or (::valikatselmus/siirto tiedot) 0))
                 ::valikatselmus/urakoitsijan-maksu (bigdec (or (::valikatselmus/urakoitsijan-maksu tiedot) 0))
                 ::valikatselmus/tilaajan-maksu (bigdec (or (::valikatselmus/tilaajan-maksu tiedot) 0))
                 ::valikatselmus/erilliskustannus-id erilliskustannus_id
                 ::valikatselmus/sanktio-id sanktio_id
                 ::valikatselmus/kulu-id kulu_id
                 ::muokkaustiedot/poistettu? false
                 ::muokkaustiedot/luoja-id (:id kayttaja)
                 ::muokkaustiedot/muokkaaja-id (:id kayttaja)
                 ::muokkaustiedot/luotu (or (::muokkaustiedot/luotu tiedot) (pvm/nyt))
                 ::muokkaustiedot/muokattu (or (::muokkaustiedot/muokattu tiedot) (pvm/nyt))}))

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
  (jdbc/with-db-transaction [db db]
    (let [urakka-id (::urakka/id tiedot)
          urakka (first (q-urakat/hae-urakka db urakka-id))
          hoitokauden-alkuvuosi (::valikatselmus/hoitokauden-alkuvuosi tiedot)
          ;; Rakennetaan valittu hoitokausi
          #_#_valittu-hoitokausi [(pvm/hoitokauden-alkupvm hoitokauden-alkuvuosi)
                                  (pvm/hoitokauden-loppupvm (inc hoitokauden-alkuvuosi))]
          _ (do
              (tarkista-valikatselmusten-urakkatyyppi urakka :paatos)
              #_(tarkista-aikavali urakka :paatos kayttaja valittu-hoitokausi))
          paatoksen-tyyppi (::valikatselmus/tyyppi tiedot)
          tavoitehinta (valikatselmus-q/hae-oikaistu-tavoitehinta db {:urakka-id urakka-id
                                                                      :hoitokauden-alkuvuosi hoitokauden-alkuvuosi})
          kattohinta (valikatselmus-q/hae-oikaistu-kattohinta db {:urakka-id urakka-id
                                                                  :hoitokauden-alkuvuosi hoitokauden-alkuvuosi})
          erilliskustannus_id (paatos-apurit/tallenna-lupausbonus db tiedot kayttaja)
          sanktio_id (paatos-apurit/tallenna-lupaussanktio db tiedot kayttaja)
          kulu_id (paatos-apurit/tallenna-kulu db tiedot kayttaja paatoksen-tyyppi)]
      (case paatoksen-tyyppi
        ::valikatselmus/tavoitehinnan-ylitys (tarkista-tavoitehinnan-ylitys tiedot tavoitehinta kattohinta)
        ::valikatselmus/kattohinnan-ylitys (tarkista-kattohinnan-ylitys tiedot urakka)
        ::valikatselmus/tavoitehinnan-alitus (tarkista-tavoitehinnan-alitus tiedot urakka tavoitehinta hoitokauden-alkuvuosi)
        ::valikatselmus/lupausbonus (paatos-apurit/tarkista-lupausbonus db kayttaja tiedot)
        ::valikatselmus/lupaussanktio (paatos-apurit/tarkista-lupaussanktio db kayttaja tiedot))
      (valikatselmus-q/tee-paatos db (tee-paatoksen-tiedot tiedot kayttaja hoitokauden-alkuvuosi erilliskustannus_id sanktio_id kulu_id)))))

(defn poista-paatos [db kayttaja {::valikatselmus/keys [paatoksen-id] :as tiedot}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu kayttaja (::urakka/id tiedot))
  (log/debug "poista-paatos :: tiedot:" (pr-str tiedot))
  (if (number? paatoksen-id)
    (jdbc/with-db-transaction [db db]
     (let [;; Poista mahdollinen lupausbonus, lupaussanktio tai kulu
           paatos (first (valikatselmus-q/hae-paatos db paatoksen-id))
           urakka-id (:urakka-id paatos)
           ;; Poista lupaussanktio, lupausbonus tai kulu jos tyyppi täsmää
           _ (cond
               (and (= (:tyyppi paatos) "lupaussanktio") (not (nil? (:sanktio_id paatos))))
               (laadunseuranta-palvelu/poista-suorasanktio db kayttaja {:id (:sanktio_id paatos) :urakka-id urakka-id})
               (and (= (:tyyppi paatos) "lupausbonus") (not (nil? (:erilliskustannus_id paatos))))
               (toteumat-palvelu/poista-erilliskustannus db kayttaja
                 {:id (:erilliskustannus_id paatos) :urakka-id urakka-id})
               (not (nil? (:kulu_id paatos)))
               (kulut-palvelu/poista-kulu-tietokannasta db kayttaja {:urakka-id urakka-id :id (:kulu_id paatos)}))
           vastaus (valikatselmus-q/poista-paatos db paatoksen-id)]
       vastaus))
    (heita-virhe "Päätöksen id puuttuu!")))

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
      (julkaise-palvelu http :poista-paatos
        (fn [user tiedot]
          (poista-paatos db user tiedot)))
      this))
  (stop [this]
    (poista-palvelut (:http-palvelin this)
      :tallenna-tavoitehinnan-oikaisu
      :hae-tavoitehintojen-oikaisut
      :poista-tavoitehinnan-oikaisu
      :tallenna-kattohinnan-oikaisu
      :hae-kattohintojen-oikaisut
      :poista-kattohinnan-oikaisu
      :hae-urakan-paatokset
      :tallenna-urakan-paatos
      :poista-paatos)
    this))
