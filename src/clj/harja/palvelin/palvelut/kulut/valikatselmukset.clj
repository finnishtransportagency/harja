(ns harja.palvelin.palvelut.kulut.valikatselmukset
  (:require
    [com.stuartsierra.component :as component]
    [slingshot.slingshot :refer [throw+ try+]]
    [specql.core :refer [columns]]
    [harja.domain.kulut.valikatselmus :as valikatselmus]
    [harja.domain.muokkaustiedot :as muokkaustiedot]
    [harja.domain.oikeudet :as oikeudet]
    [harja.domain.urakka :as urakka]
    [harja.kyselyt.urakat :as q-urakat]
    [harja.kyselyt.valikatselmus :as q]
    [harja.kyselyt.urakat :as urakat-q]
    [harja.palvelin.palvelut.lupaukset-tavoitteet.lupaukset :as lupaukset]
    [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
    [harja.pvm :as pvm]
    [harja.domain.roolit :as roolit]))

(def +maksimi-tavoitepalkkion-nosto-prosentti+ 0.03)
;; Ensimmäinen veikkaus siitä, milloin tavoitehinnan oikaisuja saa tehdä.
;; Tarkentuu myöhemmin. Huomaa, että kuukaudet menevät 0-11.
(defn oikaisujen-sallittu-aikavali []
  {:alkupvm (pvm/luo-pvm (pvm/vuosi (pvm/nyt)) 8 1)
   :loppupvm (pvm/luo-pvm (pvm/vuosi (pvm/nyt)) 11 31)})

(defn sallitussa-aikavalissa? []
  (let [sallittu-aikavali (oikaisujen-sallittu-aikavali)]
    (pvm/valissa? (pvm/nyt) (:alkupvm sallittu-aikavali) (:loppupvm sallittu-aikavali))))

(defn heita-virhe [viesti] (throw+ {:type "Error"
                                    :virheet {:koodi "ERROR" :viesti viesti}}))

(defn tarkista-aikavali
  "Tarkistaa, ollaanko kutsuhetkellä (nyt) tavoitehinnan oikaisujen tai päätösten teon sallitussa aikavälissä, eli
  Suomen aikavyöhykkeellä syyskuun 1. päivän ja joulukuun viimeisen päivän välissä. Muulloin heittää virheen."
  [urakka toimenpide kayttaja]
  (let [toimenpide-teksti (case toimenpide
                            :paatos "Urakan päätöksiä"
                            :tavoitehinnan-oikaisu "Tavoitehinnan oikaisuja")
        urakka-aktiivinen? (pvm/valissa? (pvm/nyt) (:alkupvm urakka) (:loppupvm urakka))
        sallittu-aikavali (oikaisujen-sallittu-aikavali)
        sallitussa-aikavalissa? (sallitussa-aikavalissa?)
        jvh? (roolit/jvh? kayttaja)]
    (when-not (or jvh? urakka-aktiivinen?) (heita-virhe (str toimenpide-teksti " ei voi käsitellä urakka-ajan ulkopuolella")))
    (when-not (or jvh? sallitussa-aikavalissa?) (throw+ {:type "Error"
                                                         :virheet {:koodi "ERROR" :viesti (str toimenpide-teksti " saa käsitellä ainoastaan aikavälillä "
                                                                                               (pvm/fmt-kuukausi-ja-vuosi-lyhyt (:alkupvm sallittu-aikavali)) " - "
                                                                                               (pvm/fmt-kuukausi-ja-vuosi-lyhyt (:loppupvm sallittu-aikavali)))}}))))

(defn tarkista-valikatselmusten-urakkatyyppi [urakka toimenpide]
  (let [toimenpide-teksti (case toimenpide
                            :paatos "Urakan päätöksiä"
                            :tavoitehinnan-oikaisu "Tavoitehinnan oikaisuja")]
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

(defn tarkista-tavoitehinnan-ylitys [tiedot]
  (do
    ;; Tarkista ylityksen määrä
    (tarkista-ei-siirtoa-tavoitehinnan-ylityksessa tiedot)))

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
        lupaustiedot (if (or (= 2019 urakan-alkuvuosi)
                             (= 2020 urakan-alkuvuosi))
                       (lupaukset/hae-kuukausittaiset-pisteet-hoitokaudelle db hakuparametrit)
                       (lupaukset/hae-urakan-lupaustiedot-hoitokaudelle db hakuparametrit))]
    (if (and
          ;; Varmistetaan, että tyyppi täsmää
          (= (::valikatselmus/tyyppi tiedot) ::valikatselmus/lupaus-bonus)
          ;; Varmistetaan, että lupauksissa laskettu bonus täsmää päätöksen bonukseen
          (= (bigdec (get-in lupaustiedot [:yhteenveto :bonus-tai-sanktio :bonus])) (bigdec (::valikatselmus/tilaajan-maksu tiedot))))
      true
      (heita-virhe "Lupausbonuksen tilaajan maksun summa ei täsmää lupauksissa lasketun bonuksen kanssa."))))

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
        lupaukset (if (or (= 2019 urakan-alkuvuosi)
                          (= 2020 urakan-alkuvuosi))
                    (lupaukset/hae-kuukausittaiset-pisteet-hoitokaudelle db kayttaja hakuparametrit)
                    (lupaukset/hae-urakan-lupaustiedot-hoitokaudelle db hakuparametrit))]
    (if (and
          ;; Varmistetaan, että tyyppi täsmää
          (= (::valikatselmus/tyyppi tiedot) ::valikatselmus/lupaus-sanktio)
          ;; Varmistetaan, että lupauksissa laskettu sanktio täsmää päätöksen sanktioon
          (= (bigdec (get-in lupaukset [:yhteenveto :bonus-tai-sanktio :sanktio])) (bigdec (::valikatselmus/urakoitsijan-maksu tiedot))))
      true
      (heita-virhe "Lupaussanktion urakoitsijan maksun summa ei täsmää lupauksissa lasketun sanktion kanssa."))))

(defn tarkista-kattohinnan-ylitys [tiedot urakka]
  (do
    (tarkista-ei-siirtoa-viimeisena-vuotena tiedot urakka)))

(defn tarkista-maksun-maara-alituksessa [db tiedot urakka tavoitehinta hoitokauden-alkuvuosi]
  (let [maksu (- (::valikatselmus/urakoitsijan-maksu tiedot))
        viimeinen-hoitokausi? (= (pvm/vuosi (:loppupvm urakka)) (inc hoitokauden-alkuvuosi))
        maksimi-tavoitepalkkio (* +maksimi-tavoitepalkkion-nosto-prosentti+ tavoitehinta)]
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
  (let [urakka-id (::urakka/id tiedot)
        urakka (first (q-urakat/hae-urakka db urakka-id))
        _ (do (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu
                                              kayttaja
                                              urakka-id)
              (tarkista-valikatselmusten-urakkatyyppi urakka :tavoitehinnan-oikaisu)
              (tarkista-aikavali urakka :tavoitehinnan-oikaisu kayttaja))
        oikaisun-hoitokauden-alkuvuosi (::valikatselmus/hoitokauden-alkuvuosi tiedot)
        tiedot (select-keys tiedot (columns ::valikatselmus/tavoitehinnan-oikaisu))
        oikaisu-specql (merge tiedot {::urakka/id urakka-id
                                      ::muokkaustiedot/luoja-id (:id kayttaja)
                                      ::muokkaustiedot/muokkaaja-id (:id kayttaja)
                                      ::muokkaustiedot/luotu (or (::muokkaustiedot/luotu tiedot) (pvm/nyt))
                                      ::muokkaustiedot/muokattu (or (::muokkaustiedot/muokattu tiedot) (pvm/nyt))
                                      ::valikatselmus/summa (bigdec (::valikatselmus/summa tiedot))
                                      ::valikatselmus/hoitokauden-alkuvuosi oikaisun-hoitokauden-alkuvuosi})]
    (q/poista-paatokset db oikaisun-hoitokauden-alkuvuosi)
    (if (::valikatselmus/oikaisun-id tiedot)
      (q/paivita-oikaisu db oikaisu-specql)
      (q/tee-oikaisu db oikaisu-specql))))

(defn poista-tavoitehinnan-oikaisu [db kayttaja tiedot]
  (let [oikaisu (q/hae-oikaisu db (::valikatselmus/oikaisun-id tiedot))
        urakka-id (::urakka/id oikaisu)
        urakka (first (q-urakat/hae-urakka db urakka-id))
        _ (do (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu
                                              kayttaja
                                              urakka-id)
              (tarkista-valikatselmusten-urakkatyyppi urakka :tavoitehinnan-oikaisu)
              (tarkista-aikavali urakka :tavoitehinnan-oikaisu kayttaja))]
    (q/poista-paatokset db (::valikatselmus/hoitokauden-alkuvuosi oikaisu))
    (q/poista-oikaisu db tiedot)))

(defn hae-tavoitehintojen-oikaisut [db _kayttaja tiedot]
  (let [urakka-id (::urakka/id tiedot)]
    (assert (number? urakka-id) "Virhe urakan ID:ssä.")
    (q/hae-oikaisut db tiedot)))

(defn tee-paatoksen-tiedot [tiedot kayttaja hoitokauden-alkuvuosi]
  (merge tiedot {::valikatselmus/tyyppi (name (::valikatselmus/tyyppi tiedot))
                 ::valikatselmus/hoitokauden-alkuvuosi hoitokauden-alkuvuosi
                 ::valikatselmus/siirto (bigdec (or (::valikatselmus/siirto tiedot) 0))
                 ::valikatselmus/urakoitsijan-maksu (bigdec (or (::valikatselmus/urakoitsijan-maksu tiedot) 0))
                 ::valikatselmus/tilaajan-maksu (bigdec (or (::valikatselmus/tilaajan-maksu tiedot) 0))
                 ::muokkaustiedot/luoja-id (:id kayttaja)
                 ::muokkaustiedot/muokkaaja-id (:id kayttaja)
                 ::muokkaustiedot/luotu (or (::muokkaustiedot/luotu tiedot) (pvm/nyt))
                 ::muokkaustiedot/muokattu (or (::muokkaustiedot/muokattu tiedot (pvm/nyt)))}))

(defn hae-urakan-paatokset [db kayttaja tiedot]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu
                             kayttaja
                             (::urakka/id tiedot))
  (q/hae-urakan-paatokset db tiedot))

(defn tee-paatos-urakalle [db kayttaja tiedot]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu
                                  kayttaja
                                  (::urakka/id tiedot))
  (let [urakka-id (::urakka/id tiedot)
        urakka (first (q-urakat/hae-urakka db urakka-id))
        _ (do
            (tarkista-valikatselmusten-urakkatyyppi urakka :paatos)
            (tarkista-aikavali urakka :paatos kayttaja))
        hoitokauden-alkuvuosi (::valikatselmus/hoitokauden-alkuvuosi tiedot)
        hoitokausi (alkuvuosi->hoitokausi urakka hoitokauden-alkuvuosi)
        paatoksen-tyyppi (::valikatselmus/tyyppi tiedot)
        tavoitehinta (q/hae-oikaistu-tavoitehinta db {:urakka-id urakka-id
                                                      :hoitokausi hoitokausi
                                                      :hoitokauden-alkuvuosi hoitokauden-alkuvuosi})]
    (case paatoksen-tyyppi
      ::valikatselmus/tavoitehinnan-ylitys (tarkista-tavoitehinnan-ylitys tiedot)
      ::valikatselmus/kattohinnan-ylitys (tarkista-kattohinnan-ylitys tiedot urakka)
      ::valikatselmus/tavoitehinnan-alitus (tarkista-tavoitehinnan-alitus db tiedot urakka tavoitehinta hoitokauden-alkuvuosi)
      ::valikatselmus/lupaus-bonus (tarkista-lupaus-bonus db kayttaja tiedot)
      ::valikatselmus/lupaus-sanktio (tarkista-lupaus-sanktio db kayttaja tiedot))
    (q/tee-paatos db (tee-paatoksen-tiedot tiedot kayttaja hoitokauden-alkuvuosi))))

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
      (julkaise-palvelu http :hae-urakan-paatokset
                        (fn [user tiedot]
                          (hae-urakan-paatokset db user tiedot)))
      (julkaise-palvelu http :tallenna-urakan-paatos
                        (fn [user tiedot]
                          (tee-paatos-urakalle db user tiedot)))
      this))
  (stop [this]
    (poista-palvelut (:http-palvelin this)
                     :tallenna-tavoitehinnan-oikaisu
                     :hae-tavoitehintojen-oikaisut
                     :poista-tavoitehinnan-oikaisu
                     :tallenna-urakan-paatos)
    this))