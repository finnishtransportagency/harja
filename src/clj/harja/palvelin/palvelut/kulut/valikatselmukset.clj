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

(defn tarkista-aikavali
  "Tarkistaa, ollaanko kutsuhetkellä (nyt) tavoitehinnan oikaisujen teon sallitussa aikavälissä, eli
  Suomen aikavyöhykkeellä syyskuun 1. päivän ja joulukuun viimeisen päivän välissä. Muulloin heittää virheen."
  []
  (let [sallittu-aikavali (oikaisujen-sallittu-aikavali)
        sallitussa-aikavalissa? (sallitussa-aikavalissa?)]
    (when-not sallitussa-aikavalissa? (throw+ {:type "Error"
                                               :virheet {:koodi "ERROR" :viesti (str "Tavoitehinnan oikaisuja saa tehdä, muokata tai poistaa ainoastaan aikavälillä "
                                                                                     (pvm/fmt-kuukausi-ja-vuosi-lyhyt (:alkupvm sallittu-aikavali)) " - "
                                                                                     (pvm/fmt-kuukausi-ja-vuosi-lyhyt (:loppupvm sallittu-aikavali)))}}))))

(defn tarkista-oikaisujen-urakkatyyppi [urakka]
  (when-not (= "teiden-hoito" (:tyyppi urakka))
    (throw+ {:type "Error"
             :virheet {:koodi "ERROR" :viesti "Tavoitehinnan oikaisuja saa tehdä ainoastaan teiden hoitourakoille"}})))

(defn oikaisun-hoitokausi [urakka]
  (let [nykyinen-vuosi (pvm/vuosi (pvm/nyt))
        urakan-aloitusvuosi (pvm/vuosi (:alkupvm urakka))]
    (when-not (pvm/ennen? (pvm/nyt) (:alkupvm (oikaisujen-sallittu-aikavali)))
      (- (inc nykyinen-vuosi) urakan-aloitusvuosi))))

;; Tavoitehinnan oikaisuja tehdään loppuvuodesta välikatselmuksessa.
;; Nämä summataan tai vähennetään alkuperäisestä tavoitehinnasta.
(defn tallenna-tavoitehinnan-oikaisu [db kayttaja tiedot]
  (let [urakka-id (::urakka/id tiedot)
        urakka (first (q-urakat/hae-urakka db urakka-id))
        _ (do (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu
                                              kayttaja
                                              urakka-id)
              (tarkista-oikaisujen-urakkatyyppi urakka)
              (tarkista-aikavali))
        oikaisun-hoitokausi (oikaisun-hoitokausi urakka)
        oikaisu-specql (merge tiedot {::urakka/id urakka-id
                                      ::muokkaustiedot/luoja-id (:id kayttaja)
                                      ::muokkaustiedot/muokkaaja-id (:id kayttaja)
                                      ::muokkaustiedot/luotu (or (::muokkaustiedot/luotu tiedot) (pvm/nyt))
                                      ::muokkaustiedot/muokattu (or (::muokkaustiedot/muokattu tiedot (pvm/nyt)))
                                      ::valikatselmus/summa (bigdec (::valikatselmus/summa tiedot))
                                      ::valikatselmus/hoitokausi oikaisun-hoitokausi})]
    (if (::valikatselmus/oikaisun-id tiedot)
      (q/paivita-oikaisu db oikaisu-specql)
      (q/tee-oikaisu db oikaisu-specql))))

(defn poista-tavoitehinnan-oikaisu [db kayttaja tiedot]
  (tarkista-aikavali)
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu
                                  kayttaja
                                  (::urakka/id tiedot))
  (q/poista-oikaisu db tiedot))

(defn hae-tavoitehintojen-oikaisut [db _kayttaja tiedot]
  (let [urakka-id (::urakka/id tiedot)]
    (assert (number? urakka-id) "Virhe urakan ID:ssä.")
    (q/hae-oikaisut db tiedot)))

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
      this))
  (stop [this]
    (poista-palvelut (:http-palvelin this)
                     :tallenna-tavoitehinnan-oikaisu
                     :hae-tavoitehintojen-oikaisut
                     :poista-tavoitehinnan-oikaisu)
    this))