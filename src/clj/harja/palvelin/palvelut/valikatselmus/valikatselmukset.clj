(ns harja.palvelin.palvelut.valikatselmus.valikatselmukset
  (:require
    [com.stuartsierra.component :as component]
    [harja.domain.kulut.kustannusten-seuranta :as kustannusten-seuranta]
    [harja.palvelin.palvelut.lupaus.lupaus-palvelu :as lupaus-palvelu]
    [slingshot.slingshot :refer [throw+]]
    [taoensso.timbre :as log]
    [specql.core :refer [columns]]
    [harja.tyokalut.functor :refer [fmap]]
    [harja.domain.kulut.valikatselmus :as valikatselmus]
    [harja.domain.muokkaustiedot :as muokkaustiedot]
    [harja.domain.oikeudet :as oikeudet]
    [harja.domain.urakka :as urakka]
    [harja.kyselyt.urakat :as q-urakat]
    [harja.kyselyt.valikatselmus :as valikatselmus-q]
    [harja.kyselyt.paatos-kyselyt :as paatos-kyselyt]
    [harja.kyselyt.budjettisuunnittelu :as budjettisuunnittelu-q]
    [harja.palvelin.palvelut.laadunseuranta :as laadunseuranta-palvelu]
    [harja.palvelin.palvelut.toteumat :as toteumat-palvelu]
    [harja.palvelin.palvelut.kulut.kulut :as kulut-palvelu]
    [harja.palvelin.palvelut.kulut.kustannusten-seuranta :as kustannusten-seuranta-palvelu]
    [harja.palvelin.palvelut.kulut.paatos-apurit :as paatos-apurit]
    [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
    [harja.pvm :as pvm]
    [harja.domain.roolit :as roolit]
    [harja.domain.lupaus-domain :as lupaus-domain]
    [clojure.java.jdbc :as jdbc]
    [harja.tyokalut.yleiset :refer [round2]]
    [harja.palvelin.palvelut.valikatselmus.paatosnakyvyyskone :as paatoskone]))

(defn oikaisujen-sallittu-aikavali
  "Rakennetaan sallittu aikaväli valitun hoitokauden perusteella.
  Huomaa, että kuukaudet menevät 0-11."
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
        hoitokauden-alkuvuosi (::valikatselmus/hoitokauden-alkuvuosi tiedot)
        viimeinen-vuosi? (= (pvm/vuosi (:loppupvm urakka)) (inc hoitokauden-alkuvuosi))]
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
  (tarkista-ei-siirtoa-viimeisena-vuotena tiedot urakka))

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

;; Tavoitehinnan oikaisuja tehdään loppuvuodesta välikatselmuksessa.
;; Nämä summataan tai vähennetään alkuperäisestä tavoitehinnasta.
(defn tallenna-tavoitehinnan-oikaisu [db kayttaja tiedot]
  (log/debug "tallenna-tavoitehinnan-oikaisu :: tiedot" (pr-str tiedot))
  (let [urakka-id (::urakka/id tiedot)
        urakka (first (q-urakat/hae-urakka db urakka-id))
        hoitokauden-alkuvuosi (::valikatselmus/hoitokauden-alkuvuosi tiedot)
        _ (do (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-kulut-valikatselmus
                kayttaja
                urakka-id)
            (tarkista-valikatselmusten-urakkatyyppi urakka :tavoitehinnan-oikaisu))
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
        _ (do (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-kulut-valikatselmus
                kayttaja
                urakka-id)
            (tarkista-valikatselmusten-urakkatyyppi urakka :tavoitehinnan-oikaisu))]
    (poista-urakan-paatokset db urakka-id hoitokauden-alkuvuosi kayttaja)
    (valikatselmus-q/poista-oikaisu db tiedot)))

(defn hae-tavoitehintojen-oikaisut [db kayttaja tiedot]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-kulut-valikatselmus kayttaja (::urakka/id tiedot))
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
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-kulut-valikatselmus
    kayttaja
    urakka-id)
  (jdbc/with-db-transaction [db db]
    (let [urakka (first (q-urakat/hae-urakka db urakka-id))
          _ (do
              (tarkista-valikatselmusten-urakkatyyppi urakka :kattohinnan-oikaisu)
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
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-kulut-valikatselmus
    kayttaja
    urakka-id)
  (jdbc/with-db-transaction [db db]
    (let [urakka (first (q-urakat/hae-urakka db urakka-id))
          _ (tarkista-valikatselmusten-urakkatyyppi urakka :tavoitehinnan-oikaisu)]
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
                 ::muokkaustiedot/muokkaaja-id (when (::muokkaustiedot/luotu tiedot) (:id kayttaja))
                 ::muokkaustiedot/luotu (or (::muokkaustiedot/luotu tiedot) (pvm/nyt))
                 ::muokkaustiedot/muokattu (when (::muokkaustiedot/luotu tiedot)
                                             (or (::muokkaustiedot/muokattu tiedot) (pvm/nyt)))}))

;;TODO: Tätä kutsutaan kustannusten seuranannassa. Tämä palauttaa vanhat datat. Korjaa
(defn hae-urakan-paatokset [db kayttaja tiedot]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-kulut-valikatselmus
    kayttaja
    (::urakka/id tiedot))
  (valikatselmus-q/hae-urakan-paatokset db tiedot))

#_ (defn tee-paatos-urakalle [db kayttaja tiedot]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-kulut-valikatselmus
    kayttaja
    (::urakka/id tiedot))
  (log/debug "tee-paatos-urakalle :: tiedot" (pr-str tiedot))
  (jdbc/with-db-transaction [db db]
    (let [urakka-id (::urakka/id tiedot)
          urakka (first (q-urakat/hae-urakka db urakka-id))
          hoitokauden-alkuvuosi (::valikatselmus/hoitokauden-alkuvuosi tiedot)
          _ (tarkista-valikatselmusten-urakkatyyppi urakka :paatos)
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

#_ (defn poista-paatos [db kayttaja {::valikatselmus/keys [paatoksen-id] :as tiedot}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-kulut-valikatselmus kayttaja (::urakka/id tiedot))
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
            vastaus (valikatselmus-q/poista-paatos db paatoksen-id (:id kayttaja))]
        vastaus))
    (heita-virhe "Päätöksen id puuttuu!")))

(defn hae-valikatselmuksen-tiedot-hoitovuodelle [db user {:keys [urakkaid hoitovuosi] :as tiedot}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-valitavoitteet user urakkaid)
  (let [urakan-tiedot (first (q-urakat/hae-urakka db urakkaid))
        vanha-urakka? (lupaus-domain/urakka-19-20? urakan-tiedot)
        hoitokauden-alkupvm (pvm/hoitokauden-alkupvm hoitovuosi)
        hoitokauden-loppupvm (pvm/hoitokauden-loppupvm (inc hoitovuosi))
        ;; 2019/2020 vuosille haetaan erilaiset lupausitiedot
        lupaus-parametrit {:urakka-id urakkaid
                           :valittu-hoitokausi [hoitokauden-alkupvm
                                                hoitokauden-loppupvm]
                           :nykyhetki (pvm/nyt)}
        lupaustiedot (if vanha-urakka?
                       (lupaus-palvelu/hae-kuukausittaiset-pisteet-hoitokaudelle db lupaus-parametrit)
                       (lupaus-palvelu/hae-urakan-lupaustiedot-hoitokaudelle db lupaus-parametrit))
        paatokset (valikatselmus-q/hae-urakan-paatokset-hoitovuodelle db urakkaid hoitovuosi)
        tavoitehinnan-muutokset (valikatselmus-q/hae-oikaisut db {::urakka/id urakkaid})
        ;; UI haluaa tavoitehinnan muutokset tietyssä formaatissa. Formatoidaan ne tässä, eikä ui:lla, kuten ennen
        ;; Data on muodossa {vuosi [{data} {data}]}
        ;; Muutetaan se {vuosi {0 {data}
        ;;                      1 {data}}}
        tavoitehinnan-muutokset (fmap #(zipmap (range) (map (fn [o] (-> o
                                                                      (assoc :koskematon true)
                                                                      (assoc :lisays-tai-vahennys (if (neg? (::valikatselmus/summa o))
                                                                                                    :vahennys
                                                                                                    :lisays)))) %))
                                  tavoitehinnan-muutokset)
        kattohinnan-muutokset (valikatselmus-q/hae-kattohinnan-oikaisut db {::urakka/id urakkaid})
        kustannukset (kustannusten-seuranta-palvelu/hae-urakan-kustannusten-seuranta-paaryhmittain
                       db user {:urakka-id urakkaid
                                :hoitokauden-alkuvuosi hoitovuosi
                                :alkupvm hoitokauden-alkupvm
                                :loppupvm hoitokauden-loppupvm})
        ;; Formatoidaan kustannukset ui:ta varten
        kustannukset-jarjestettyna (kustannusten-seuranta/jarjesta-tehtavat kustannukset)
        budjettitavoite (budjettisuunnittelu-q/hae-budjettitavoite db {:urakka urakkaid})

        ;; Kustannusten mukana ei tule tarvittavalla tasolla erotettuna bonuksia. Joten haetaan ne erikseen
        bonukset (valikatselmus-q/hae-bonukset db {:urakka-id urakkaid
                                                   :alkupvm hoitokauden-alkupvm
                                                   :loppupvm hoitokauden-loppupvm})
        ;; Kustannusten mukana ei tule tarvittavalla tasolla erotettuna sanktioita. Joten haetaan ne erikseen
        sanktiot (valikatselmus-q/hae-sanktiot db {:urakka-id urakkaid
                                                   :alkupvm hoitokauden-alkupvm
                                                   :loppupvm hoitokauden-loppupvm})]
    {:lupaustiedot (dissoc lupaustiedot :lupausryhmat :lahtotiedot)
     :paatokset paatokset
     :tavoitehinnan-muutokset tavoitehinnan-muutokset
     :kattohinnan-muutokset kattohinnan-muutokset
     :kustannukset-yhteensa (:yhteensa kustannukset-jarjestettyna)
     :kustannukset (:taulukon-rivit kustannukset-jarjestettyna)
     :bonukset bonukset
     :sanktiot sanktiot
     :budjettitavoite budjettitavoite}))

(defn tee-lupauspaatos [db kayttaja paatos]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-kulut-valikatselmus kayttaja (:urakkaid paatos))
  (log/debug "tee-lupauspaatos :: paatos" (pr-str paatos))
  (jdbc/with-db-transaction [db db]
    (let [validaatio #{}
          urakka-id (:urakkaid paatos)
          urakka (first (q-urakat/hae-urakka db urakka-id))
          hoitokauden-alkuvuosi (:hoitokauden_alkuvuosi paatos)
          ;; Verrataan tietokannan tavoitehintaa saatuun tavoitehintaan
          tavoitehinta (valikatselmus-q/hae-oikaistu-tavoitehinta db {:urakka-id urakka-id
                                                                      :hoitokauden-alkuvuosi hoitokauden-alkuvuosi})
          validaatio (if-not (= tavoitehinta (:tavoitehinta paatos))
                       (conj validaatio (str "Tavoitehinta ei täsmää suunnitelman kanssa. Suunniteltu tavoitehinta:" tavoitehinta "€. Päätöksen mukainen tavoitehinta: " (:tavoitehinta paatos) " €"))
                       validaatio)
          kattohinta (valikatselmus-q/hae-oikaistu-kattohinta db {:urakka-id urakka-id
                                                                  :hoitokauden-alkuvuosi hoitokauden-alkuvuosi})
          validaatio (if-not (= kattohinta (:kattohinta paatos))
                       (conj validaatio (str "Kattohinta ei täsmää suunnitelman kanssa. Suunniteltu kattohinta:" kattohinta " €. Päätöksen mukainen kattohinta: " (:kattohinta paatos) " €"))
                       validaatio)
          ;; Jos ollaan tekemässä lupauspäätöstä, josta tulee bonusta
          erilliskustannus_id (when (and "bonus" (:tyyppi paatos) (:lupausbonus paatos))
                                (paatos-apurit/tallenna-lupausbonus db paatos kayttaja))
          ;; Tai jos tulee sakkoja, niin tehdään sanktio
          sanktio_id (when (and "sakko" (:tyyppi paatos) (:lupaussanktio paatos))
                       (paatos-apurit/tallenna-lupaussanktio db paatos kayttaja))
          paatos (-> paatos
                   (assoc :erilliskustannus_id erilliskustannus_id)
                   (assoc :sanktio_id sanktio_id))]
      (if (seq validaatio)
        (heita-virhe (str "Virheellinen päätös: " (clojure.string/join ", " validaatio)))
        (paatos-kyselyt/tee-lupauspaatos db {:urakkaid paatos} paatos)))))

(defn poista-lupauspaatos [db kayttaja paatos]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-kulut-valikatselmus kayttaja (:urakkaid paatos))
  (log/debug "poista-lupauspaatos :: paatos" (pr-str paatos))
  ;; Wrapataan transactioniin, jotta voidaan poistaa myös lupausbonukset ja -sanktiot
  (jdbc/with-db-transaction [db db]
    (let [paatos-tietokannasta (first (paatos-kyselyt/hae-lupauspaatos db {:paatos-id (:id paatos)}))
          ;; Jos paatoksella on erilliskustannus, niin poista bonus
          _ (when (:erilliskustannus_id paatos-tietokannasta)
              (toteumat-palvelu/poista-erilliskustannus db kayttaja
                {:id (:erilliskustannus_id paatos-tietokannasta) :urakka-id (:urakkaid paatos-tietokannasta)}))
          ;; Jos päätöksellä on sanktio, niin poista sanktio
          _ (when (:sanktio_id paatos-tietokannasta)
              (laadunseuranta-palvelu/poista-suorasanktio db kayttaja {:id (:sanktio_id paatos-tietokannasta)
                                                                       :urakka-id (:urakkaid paatos-tietokannasta)}))
          _ (paatos-kyselyt/poista-lupauspaatos db (:urakkaid paatos-tietokannasta) (:id kayttaja) (:id paatos-tietokannasta))]
      ;; Palautetaan poistetun päätöksen id
      (:id paatos-tietokannasta))))

(defn tee-tavoitehinnan-muutospaatos [db kayttaja paatos]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-kulut-valikatselmus kayttaja (:urakkaid paatos))
  (log/debug "tee-tavoitehinnan-muutospaatos :: paatos" (pr-str paatos))
  (jdbc/with-db-transaction [db db]
    (let [validaatio #{}
          urakka-id (:urakkaid paatos)
          urakka (first (q-urakat/hae-urakka db urakka-id))
          hoitokauden-alkuvuosi (:hoitokauden_alkuvuosi paatos)
          versio (:versio paatos)
          ;; Verrataan tietokannan tavoitehintaa saatuun tavoitehintaan
          tavoitehinta (valikatselmus-q/hae-oikaistu-tavoitehinta db {:urakka-id urakka-id
                                                                      :hoitokauden-alkuvuosi hoitokauden-alkuvuosi})
          validaatio (if-not (= tavoitehinta (:tavoitehinta paatos))
                       (conj validaatio (str "Tavoitehinta ei täsmää suunnitelman kanssa. Suunniteltu tavoitehinta:" tavoitehinta "€. Päätöksen mukainen tavoitehinta: " (:tavoitehinta paatos) " €"))
                       validaatio)
          kattohinta (valikatselmus-q/hae-oikaistu-kattohinta db {:urakka-id urakka-id
                                                                  :hoitokauden-alkuvuosi hoitokauden-alkuvuosi})
          validaatio (if-not (= kattohinta (:kattohinta paatos))
                       (conj validaatio (str "Kattohinta ei täsmää suunnitelman kanssa. Suunniteltu kattohinta:" kattohinta " €. Päätöksen mukainen kattohinta: " (:kattohinta paatos) " €"))
                       validaatio)]
      (if (seq validaatio)
        (heita-virhe (str "Virheellinen päätös: " (clojure.string/join ", " validaatio)))
        (paatos-kyselyt/tee-tavoitehinnan-muutospaatos db urakka-id paatos)))))

(defn poista-tavoitehinnan-muutospaatos [db kayttaja paatos]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-kulut-valikatselmus kayttaja (:urakkaid paatos))
  (log/debug "poista-tavoitehinnan-muutospaatos :: paatos" (pr-str paatos))
  (paatos-kyselyt/poista-tavoitehinnan-muutospaatos db (:urakkaid paatos) (:id kayttaja) (:id paatos)))

(defn tee-tavoitehinnan-alituspaatos [db kayttaja paatos]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-kulut-valikatselmus kayttaja (:urakkaid paatos))
  (log/debug "tee-tavoitehinnan-alituspaatos :: paatos" (pr-str paatos))
  (jdbc/with-db-transaction [db db]
    (let [validaatio #{}
          urakka-id (:urakkaid paatos)
          urakka (first (q-urakat/hae-urakka db urakka-id))
          hoitokauden-alkuvuosi (:hoitokauden_alkuvuosi paatos)
          viimeinen-hoitovuosi? (= hoitokauden-alkuvuosi (dec (-> urakka :loppupvm pvm/vuosi)))
          versio (:versio paatos)
          ;; Verrataan tietokannan tavoitehintaa saatuun tavoitehintaan
          tavoitehinta (valikatselmus-q/hae-oikaistu-tavoitehinta db {:urakka-id urakka-id
                                                                      :hoitokauden-alkuvuosi hoitokauden-alkuvuosi})
          validaatio (if-not (= tavoitehinta (:tavoitehinta paatos))
                       (conj validaatio (str "Tavoitehinta ei täsmää suunnitelman kanssa. Suunniteltu tavoitehinta:" tavoitehinta "€. Päätöksen mukainen tavoitehinta: " (:tavoitehinta paatos) " €"))
                       validaatio)
          ;; Jos validointi on kunnossa, niin luodaan tavoitepalkkiokulu
          kulu_id (when-not (seq validaatio)
                    ;; Viimeisenä hoitovuotena tehdään tilaajalle kululasku koko alituksesta. Muuten vain tavoitepalkkio
                    (if-not viimeinen-hoitovuosi?
                      (paatos-apurit/tallenna-kulu db paatos kayttaja :tavoitehinnan-alitus (:tavoitepalkkio paatos))
                      (paatos-apurit/tallenna-kulu db paatos kayttaja :tavoitehinnan-alitus (:alituksen_maara paatos))))
          paatos (assoc paatos :kulu_id kulu_id)]
      (if (seq validaatio)
        (heita-virhe (str "Virheellinen päätös: " (clojure.string/join ", " validaatio)))
        (paatos-kyselyt/tee-tavoitehinnan-alituspaatos db urakka-id paatos)))))

(defn poista-tavoitehinnan-alituspaatos [db kayttaja paatos]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-kulut-valikatselmus kayttaja (:urakkaid paatos))
  (log/debug "poista-tavoitehinnan-alituspaatos :: paatos" (pr-str paatos))
  (jdbc/with-db-transaction [db db]
    (let [;; Jos päätöksellä on kulu, niin poisteatan se samalla
          _ (when (:kulu_id paatos)
              (kulut-palvelu/poista-kulu-tietokannasta db kayttaja
                {:urakka-id (:urakkaid paatos) :id (:kulu_id paatos)}))

          ]
      (paatos-kyselyt/poista-tavoitehinnan-alituspaatos db (:urakkaid paatos) (:id kayttaja) (:id paatos)))))

(defn tee-tavoitehinnan-ylityspaatos [db kayttaja paatos]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-kulut-valikatselmus kayttaja (:urakkaid paatos))
  (log/debug "tee-tavoitehinnan-ylityspaatos :: paatos" (pr-str paatos))
  (jdbc/with-db-transaction [db db]
    (let [validaatio #{}
          urakka-id (:urakkaid paatos)
          urakka (first (q-urakat/hae-urakka db urakka-id))
          hoitokauden-alkuvuosi (:hoitokauden_alkuvuosi paatos)
          versio (:versio paatos)
          ;; Verrataan tietokannan tavoitehintaa saatuun tavoitehintaan
          tavoitehinta (valikatselmus-q/hae-oikaistu-tavoitehinta db {:urakka-id urakka-id
                                                                      :hoitokauden-alkuvuosi hoitokauden-alkuvuosi})
          validaatio (if-not (= tavoitehinta (:tavoitehinta paatos))
                       (conj validaatio (str "Tavoitehinta ei täsmää suunnitelman kanssa. Suunniteltu tavoitehinta:" tavoitehinta "€. Päätöksen mukainen tavoitehinta: " (:tavoitehinta paatos) " €"))
                       validaatio)

          ;; TODO: Tee lisää validaatiota. Varmista, että urakoitsijan ja tilaanjan prosentit täsmää versioihin

          ;; Jos validointi on kunnossa, niin luodaan tavoitehinnan ylityskulu - jonka maksaa urakoitsija
          kulu_id (when-not (seq validaatio)
                    (paatos-apurit/tallenna-kulu db paatos kayttaja :tavoitehinnan-ylitys (:tilaaja_maksaa paatos)))
          paatos (assoc paatos :kulu_id kulu_id)]
      (if (seq validaatio)
        (heita-virhe (str "Virheellinen päätös: " (clojure.string/join ", " validaatio)))
        (paatos-kyselyt/tee-tavoitehinnan-ylityspaatos db urakka-id paatos)))))

(defn poista-tavoitehinnan-ylityspaatos [db kayttaja paatos]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-kulut-valikatselmus kayttaja (:urakkaid paatos))
  (log/debug "poista-tavoitehinnan-ylityspaatos :: paatos" (pr-str paatos))
  (jdbc/with-db-transaction [db db]
    (let [;; Jos päätöksellä on kulu, niin poisteatan se samalla
          _ (when (:kulu_id paatos)
              (kulut-palvelu/poista-kulu-tietokannasta db kayttaja
                {:urakka-id (:urakkaid paatos) :id (:kulu_id paatos)}))]
      (paatos-kyselyt/poista-tavoitehinnan-ylityspaatos db (:urakkaid paatos) (:id kayttaja) (:id paatos)))))

(defn tee-kattohinnan-ylityspaatos [db kayttaja paatos]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-kulut-valikatselmus kayttaja (:urakkaid paatos))
  (log/debug "tee-kattohinnan-ylityspaatos :: paatos" (pr-str paatos))
  (jdbc/with-db-transaction [db db]
    (let [validaatio #{}
          urakka-id (:urakkaid paatos)
          urakka (first (q-urakat/hae-urakka db urakka-id))
          hoitokauden-alkuvuosi (:hoitokauden_alkuvuosi paatos)
          versio (:versio paatos)
          ;; Verrataan tietokannan kattohintaa saatuun kattohintaan
          kattohinta (valikatselmus-q/hae-oikaistu-kattohinta db {:urakka-id urakka-id
                                                                  :hoitokauden-alkuvuosi hoitokauden-alkuvuosi})
          validaatio (if-not (= kattohinta (:kattohinta paatos))
                       (conj validaatio (str "Kattohinta ei täsmää suunnitelman kanssa. Suunniteltu kattohinta:" kattohinta " €. Päätöksen mukainen kattohinta: " (:kattohinta paatos) " €"))
                       validaatio)

          ;; TODO: Tee lisää validaatiota. Varmista, että urakoitsijan ja tilaanjan prosentit täsmää versioihin
          ;; Jos validointi on kunnossa, niin luodaan kattohinnan ylityskulu - jonka maksaa urakoitsija
          kulu_id (when-not (seq validaatio)
                    (paatos-apurit/tallenna-kulu db paatos kayttaja :kattohinnan-ylitys (:urakoitsija_maksaa paatos)))
          paatos (assoc paatos :kulu_id kulu_id)]
      (if (seq validaatio)
        (heita-virhe (str "Virheellinen päätös: " (clojure.string/join ", " validaatio)))
        (paatos-kyselyt/tee-kattohinnan-ylityspaatos db urakka-id paatos)))))

(defn poista-kattohinnan-ylityspaatos [db kayttaja paatos]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-kulut-valikatselmus kayttaja (:urakkaid paatos))
  (log/debug "poista-kattohinnan-ylityspaatos :: paatos" (pr-str paatos))
  (jdbc/with-db-transaction [db db]
    (let [;; Jos päätöksellä on kulu, niin poisteatan se samalla
          _ (when (:kulu_id paatos)
              (kulut-palvelu/poista-kulu-tietokannasta db kayttaja
                {:urakka-id (:urakkaid paatos) :id (:kulu_id paatos)}))]
      (paatos-kyselyt/poista-kattohinnan-ylityspaatos db (:urakkaid paatos) (:id kayttaja) (:id paatos)))))

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
      #_ (julkaise-palvelu http :tallenna-urakan-paatos
        (fn [user tiedot]
          (tee-paatos-urakalle db user tiedot)))
      #_ (julkaise-palvelu http :poista-paatos
        (fn [user tiedot]
          (poista-paatos db user tiedot)))
      (julkaise-palvelu (:http-palvelin this)
        :hae-valikatselmuksen-tiedot-hoitovuodelle
        (fn [user tiedot]
          (hae-valikatselmuksen-tiedot-hoitovuodelle (:db this) user tiedot)))
      (julkaise-palvelu (:http-palvelin this)
        :tee-lupauspaatos
        (fn [user tiedot]
          (tee-lupauspaatos (:db this) user tiedot)))
      (julkaise-palvelu (:http-palvelin this)
        :poista-lupauspaatos
        (fn [user tiedot]
          (poista-lupauspaatos (:db this) user tiedot)))
      (julkaise-palvelu (:http-palvelin this)
        :tee-tavoitehinnan-muutospaatos
        (fn [user tiedot]
          (tee-tavoitehinnan-muutospaatos (:db this) user tiedot)))
      (julkaise-palvelu (:http-palvelin this)
        :poista-tavoitehinnan-muutospaatos
        (fn [user tiedot]
          (poista-tavoitehinnan-muutospaatos (:db this) user tiedot)))
      (julkaise-palvelu (:http-palvelin this)
        :tee-tavoitehinnan-alituspaatos
        (fn [user tiedot]
          (tee-tavoitehinnan-alituspaatos (:db this) user tiedot)))
      (julkaise-palvelu (:http-palvelin this)
        :poista-tavoitehinnan-alituspaatos
        (fn [user tiedot]
          (poista-tavoitehinnan-alituspaatos (:db this) user tiedot)))
      (julkaise-palvelu (:http-palvelin this)
        :tee-tavoitehinnan-ylityspaatos
        (fn [user tiedot]
          (tee-tavoitehinnan-ylityspaatos (:db this) user tiedot)))
      (julkaise-palvelu (:http-palvelin this)
        :poista-tavoitehinnan-ylityspaatos
        (fn [user tiedot]
          (poista-tavoitehinnan-ylityspaatos (:db this) user tiedot)))
      (julkaise-palvelu (:http-palvelin this)
        :tee-kattohinnan-ylityspaatos
        (fn [user tiedot]
          (tee-kattohinnan-ylityspaatos (:db this) user tiedot)))
      (julkaise-palvelu (:http-palvelin this)
        :poista-kattohinnan-ylityspaatos
        (fn [user tiedot]
          (poista-kattohinnan-ylityspaatos (:db this) user tiedot)))
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
      #_ :tallenna-urakan-paatos
      #_ :poista-paatos
      :hae-valikatselmuksen-tiedot-hoitovuodelle
      :tee-lupauspaatos
      :poista-lupauspaatos
      :tee-tavoitehinnan-muutospaatos
      :poista-tavoitehinnan-muutospaatos
      :tee-tavoitehinnan-alituspaatos
      :poista-tavoitehinnan-alituspaatos
      :tee-tavoitehinnan-ylityspaatos
      :poista-tavoitehinnan-ylityspaatos
      :tee-kattohinnan-ylityspaatos
      :poista-kattohinnan-ylityspaatos)
    this))
