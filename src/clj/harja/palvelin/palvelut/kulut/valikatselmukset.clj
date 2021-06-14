(ns harja.palvelin.palvelut.kulut.valikatselmukset
  (:require
    [com.stuartsierra.component :as component]
    [slingshot.slingshot :refer [throw+ try+]]
    [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
    [harja.kyselyt.urakat :as q-urakat]
    [harja.kyselyt.valikatselmus :as q]
    [harja.domain.kulut.valikatselmus :as valikatselmus]
    [harja.domain.urakka :as urakka]
    [harja.domain.muokkaustiedot :as muokkaustiedot]
    [harja.pvm :as pvm]))

;; Ensimmäinen veikkaus siitä, milloin tavoitehinnan oikaisuja saa tehdä.
;; Tarkentuu myöhemmin. Huomaa, että kuukaudet menevät 0-11.
(defn oikaisujen-sallittu-aikavali []
  {:alkupvm (pvm/luo-pvm (pvm/vuosi (pvm/nyt)) 8 1)
   :loppupvm (pvm/luo-pvm (pvm/vuosi (pvm/nyt)) 11 31)})

(defn oikaisun-hoitokausi [urakka]
  (let [nykyinen-vuosi (pvm/vuosi (pvm/nyt))
        urakan-aloitusvuosi (pvm/vuosi (:alkupvm urakka))]
    (when-not (pvm/ennen? (pvm/nyt) (:alkupvm (oikaisujen-sallittu-aikavali)))
      (- (inc nykyinen-vuosi) urakan-aloitusvuosi))))

(defn tallenna-tavoitehinnan-oikaisu [db kayttaja tiedot]
  (let [urakka-id (::urakka/id tiedot)
        urakka (first (q-urakat/hae-urakka db urakka-id))
        sallittu-aikavali (oikaisujen-sallittu-aikavali)
        sallitussa-aikavalissa? (pvm/valissa? (pvm/nyt) (:alkupvm sallittu-aikavali) (:loppupvm sallittu-aikavali))
        _ (when-not sallitussa-aikavalissa? (throw+ {:type "Error"
                                                     :virheet {:koodi "ERROR" :viesti (str "Tavoitehinnan oikaisuja saa tehdä ainoastaan aikavälillä "
                                                                                           (pvm/fmt-kuukausi-ja-vuosi-lyhyt (:alkupvm sallittu-aikavali))
                                                                                           " - "
                                                                                           (pvm/fmt-kuukausi-ja-vuosi-lyhyt (:loppupvm sallittu-aikavali)))}}))
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
                          (poista-tavoitehinnan-oikaisu db user tiedot)))))
  (stop [this]
    (poista-palvelut (:http-palvelin this)
                     :tallenna-tavoitehinnan-oikaisu
                     :hae-tavoitehintojen-oikaisut)))