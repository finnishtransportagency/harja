(ns harja.palvelin.palvelut.kulut.valikatselmukset
  (:require
    [com.stuartsierra.component :as component]
    [slingshot.slingshot :refer [throw+ try+]]
    [harja.domain.kulut.valikatselmus :as valikatselmus]
    [harja.domain.muokkaustiedot :as muokkaustiedot]
    [harja.domain.oikeudet :as oikeudet]
    [harja.domain.urakka :as urakka]
    [harja.kyselyt.urakat :as q-urakat]
    [harja.kyselyt.valikatselmus :as q]
    [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
    [harja.pvm :as pvm]))

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
  [urakka toimenpide]
  (let [toimenpide-teksti (case toimenpide
                            :paatos "Urakan päätöksiä"
                            :tavoitehinnan-oikaisu "Tavoitehinnan oikaisuja")
        urakka-aktiivinen? (pvm/valissa? (pvm/nyt) (:alkupvm urakka) (:loppupvm urakka))
        sallittu-aikavali (oikaisujen-sallittu-aikavali)
        sallitussa-aikavalissa? (sallitussa-aikavalissa?)]
    (when-not urakka-aktiivinen? (heita-virhe (str toimenpide-teksti " ei voi käsitellä urakka-ajan ulkopuolella")))
    (when-not sallitussa-aikavalissa? (throw+ {:type "Error"
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
                     (< 0 siirto))
        viimeinen-vuosi? (= (pvm/vuosi (:loppupvm urakka)) (pvm/vuosi (pvm/nyt)))]
    (if (and siirto? viimeinen-vuosi?) (heita-virhe "Kattohinnan ylitystä ei voi siirtää ensi vuodelle urakan viimeisenä vuotena"))))

(defn tarkista-ei-siirtoa-tavoitehinnan-ylityksessa [tiedot]
  (let [siirto (::valikatselmus/siirto tiedot)
        siirto? (and (some? siirto)
                     (< 0 siirto))]
    (if siirto? (heita-virhe "Tavoitehinnan ylitystä ei voi siirtää ensi vuodelle"))))

(defn tarkista-tavoitehinnan-ylitys [tiedot]
  (do
    ;; Tarkista ylityksen määrä
    (tarkista-ei-siirtoa-tavoitehinnan-ylityksessa tiedot)))

(defn tarkista-kattohinnan-ylitys [tiedot urakka]
  (do
    (tarkista-ei-siirtoa-viimeisena-vuotena tiedot urakka)))

(defn hoitokausi [urakka]
  (let [nykyinen-vuosi (pvm/vuosi (pvm/nyt))
        urakan-aloitusvuosi (pvm/vuosi (:alkupvm urakka))]
    (when-not (pvm/ennen? (pvm/nyt) (:alkupvm (oikaisujen-sallittu-aikavali)))
      (- (inc nykyinen-vuosi) urakan-aloitusvuosi))))

;; Funktio olemassa sen varalta, että oikaisuja tai päätöksiä voikin tehdä laajemmalla aikavälillä mitä alunperin veikattiin.
(defn hoitokauden-alkuvuosi []
  (dec (pvm/vuosi (pvm/nyt))))

;; Tavoitehinnan oikaisuja tehdään loppuvuodesta välikatselmuksessa.
;; Nämä summataan tai vähennetään alkuperäisestä tavoitehinnasta.
(defn tallenna-tavoitehinnan-oikaisu [db kayttaja tiedot]
  (let [urakka-id (::urakka/id tiedot)
        urakka (first (q-urakat/hae-urakka db urakka-id))
        _ (do (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu
                                              kayttaja
                                              urakka-id)
              (tarkista-valikatselmusten-urakkatyyppi urakka :tavoitehinnan-oikaisu)
              (tarkista-aikavali urakka :tavoitehinnan-oikaisu))
        oikaisun-hoitokauden-alkuvuosi (hoitokauden-alkuvuosi)
        oikaisu-specql (merge tiedot {::urakka/id urakka-id
                                      ::muokkaustiedot/luoja-id (:id kayttaja)
                                      ::muokkaustiedot/muokkaaja-id (:id kayttaja)
                                      ::muokkaustiedot/luotu (or (::muokkaustiedot/luotu tiedot) (pvm/nyt))
                                      ::muokkaustiedot/muokattu (or (::muokkaustiedot/muokattu tiedot (pvm/nyt)))
                                      ::valikatselmus/summa (bigdec (::valikatselmus/summa tiedot))
                                      ::valikatselmus/hoitokauden-alkuvuosi oikaisun-hoitokauden-alkuvuosi})]
    (if (::valikatselmus/oikaisun-id tiedot)
      (q/paivita-oikaisu db oikaisu-specql)
      (q/tee-oikaisu db oikaisu-specql))))

(defn poista-tavoitehinnan-oikaisu [db kayttaja tiedot]
  (let [urakka-id (::urakka/id tiedot)
        urakka (first (q-urakat/hae-urakka db urakka-id))]
    (tarkista-aikavali urakka :tavoitehinnan-oikaisu)
    (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu
                                    kayttaja
                                    (::urakka/id tiedot))
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


(defn tee-paatos-urakalle [db kayttaja tiedot]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu
                                  kayttaja
                                  (::urakka/id tiedot))
  (let [urakka-id (::urakka/id tiedot)
        urakka (first (q-urakat/hae-urakka db urakka-id))
        _ (do
            (tarkista-valikatselmusten-urakkatyyppi urakka :paatos)
            (tarkista-aikavali urakka :paatos))
        hoitokauden-alkuvuosi (hoitokauden-alkuvuosi)
        hoitokausi (hoitokausi urakka)
        paatoksen-tyyppi (::valikatselmus/tyyppi tiedot)
        tavoitehinta (:tavoitehinta (q/hae-oikaistu-tavoitehinta db {:urakka-id urakka-id
                                                                     :hoitokausi hoitokausi
                                                                     :hoitokauden-alkuvuosi hoitokauden-alkuvuosi}))]
    (case paatoksen-tyyppi
      ::valikatselmus/tavoitehinnan-ylitys (tarkista-tavoitehinnan-ylitys tiedot)
      ::valikatselmus/kattohinnan-ylitys (tarkista-kattohinnan-ylitys tiedot urakka))
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