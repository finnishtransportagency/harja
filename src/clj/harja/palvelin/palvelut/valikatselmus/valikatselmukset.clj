(ns harja.palvelin.palvelut.valikatselmus.valikatselmukset
  (:require
    [com.stuartsierra.component :as component]
    [cognitect.transit :as transit]
    [clojure.string :as string]
    [harja.domain.kulut.kustannusten-seuranta :as kustannusten-seuranta]
    [harja.kyselyt.rahavaraukset :as rahavaraus-kyselyt]
    [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]
    [harja.palvelin.palvelut.indeksit :as indeksipalvelu]
    [harja.palvelin.palvelut.lupaus.lupaus-palvelu :as lupaus-palvelu]
    [slingshot.slingshot :refer [throw+]]
    [taoensso.timbre :as log]
    [specql.core :refer [columns]]
    [harja.tyokalut.functor :refer [fmap]]
    [harja.tyokalut.yleiset :refer [round2]]
    [harja.domain.kulut.valikatselmus :as valikatselmus]
    [harja.domain.muokkaustiedot :as muokkaustiedot]
    [harja.domain.oikeudet :as oikeudet]
    [harja.domain.urakka :as urakka]
    [harja.domain.valikatselmus :as valikatselmus-domain]
    [harja.kyselyt.konversio :as konversio]
    [harja.kyselyt.urakat :as q-urakat]
    [harja.kyselyt.valikatselmus :as valikatselmus-q]
    [harja.kyselyt.paatos-kyselyt :as paatos-kyselyt]
    [harja.kyselyt.budjettisuunnittelu :as budjettisuunnittelu-q]
    [harja.kyselyt.indeksit :as indeksi-kyselyt]
    [harja.kyselyt.jarjestelman-tila :as jarjestelma-kyselyt]
    [harja.palvelin.palvelut.laadunseuranta :as laadunseuranta-palvelu]
    [harja.palvelin.palvelut.toteumat :as toteumat-palvelu]
    [harja.palvelin.palvelut.kulut.kulut :as kulut-palvelu]
    [harja.palvelin.palvelut.kulut.kustannusten-seuranta :as kustannusten-seuranta-palvelu]
    [harja.palvelin.palvelut.kulut.paatos-apurit :as paatos-apurit]
    [harja.palvelin.palvelut.muutos.muutos-palvelu :as muutos-palvelu]
    [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut transit-vastaus]]
    [harja.pvm :as pvm]
    [harja.domain.lupaus-domain :as lupaus-domain]
    [clojure.java.jdbc :as jdbc]
    [harja.palvelin.palvelut.valikatselmus.paatosnakyvyyskone :as paatoskone]))

(defn hoitokaudet-vektorimuotoon
  "Muuntaa hoitokaudet {:alkupvm .. :loppupvm ..} -muodosta vektoreiksi [alkupvm loppupvm],
   missä loppupvm on hoitokauden viimeinen sekunti (alkuperäinen loppupvm + 24h - 1s)."
  [hoitokaudet]
  (mapv (fn [{:keys [alkupvm loppupvm]}]
          [alkupvm loppupvm])
    hoitokaudet))

(defn heita-virhe [viesti]
  (throw+ viesti))

(defn tarkista-valikatselmusten-urakkatyyppi [urakka toimenpide]
  (let [toimenpide-teksti (case toimenpide
                            :paatos "Urakan päätöksiä"
                            :tavoitehinnan-oikaisu "Tavoitehinnan oikaisuja"
                            :kattohinnan-oikaisu "Kattohinnan oikaisuja")]
    (when-not (= "teiden-hoito" (:tyyppi urakka))
      (throw+ {:type "Error"
               :virheet {:koodi "ERROR" :viesti (str toimenpide-teksti " saa tehdä ainoastaan teiden hoitourakoille")}}))))

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

(defn hae-hoitovuoden-indeksiluvut [db urakkaid hoitovuosi]
  (let [;; Palauttaa indeksit kuukausinumeroilla
        indeksit (indeksi-kyselyt/hae-urakan-hoitovuoden-indeksit-kuukausinimilla db {:urakkaid urakkaid
                                                                                      :vuosi hoitovuosi})
        ;; Muokataan kuukausinumerot kuukausien nimiksi
        indeksiluvut (map (fn [rivi]
                            (let [kuukausinimi (pvm/kk-pitka-fmt (:kuukausi rivi))]
                              (-> rivi
                                (assoc :kuukausi (str (:vuosi rivi) " " kuukausinimi))
                                (dissoc :vuosi))))
                       indeksit)]
    indeksiluvut))

(defn maarita-hv-lopun-indeksikorjaamaton-tavoitehinta
  "Useimmat päätökset vaativat tietää hoitovuoden lopun tavoitehinnan.
  Sitä ei ole ikävä kyllä tallennettu kantaan valmiiksi.
  Tällä helpperillä määrittetään hoitovuoden lopun tavoitehinta, jossa on mukana indeksikorjauspäätöksen vaikutus, mikäli päätös on tehty.

  Budjettitavoite-vuodelle parametrin sisältämä tavoitehinta-oikaistu arvo sisältää mahdolliset tavoitehinnan oikaisut,
  joita on tehty 2024 ja sitä aemmin alkaneille urakoille."
  [db kayttaja hoitokauden-alkuvuosi valittu-hoitokausi urakka-id urakan-alkuvuosi budjettitavoite-vuodelle]
  (let [urakan-parametrit (first (q-urakat/hae-urakan-parametrit db {:urakkaid urakka-id}))
        ;; Haetaan pysyvät muutokset, jotka vaikuttavat tähän hoitovuoteen
        ;; Haetaan indeksikorjauksen vaatimat tavoitehinnan muutokset
        aktiiviset-muutokset (:muutos-summa budjettitavoite-vuodelle)

        ;; Muutosten aiheuttamat muutokset tavoitehinnassa
        muutos-rahavaraukset (rahavaraus-kyselyt/muutosten-rahavaraukset db urakka-id hoitokauden-alkuvuosi)
        ;; Hae kaikki tehtävä ja määrämuutokset - jos ne on urakalla käytössä
        ;; Määrämuutosten hakeminen on superhidasta, kun urakalla on paljon toteumia.
        ;; Joten vältetään sitä, jos mahdollista.
        tehtava-ja-maaramuutokset (when (:muutosten_hallinta urakan-parametrit)
                                    (muutos-palvelu/hae-tehtava-maaramuutokset db kayttaja
                                      {:urakka-id urakka-id
                                       :laskenta-automatiikka? true
                                       :hoitokaudet (hoitokaudet-vektorimuotoon (q-urakat/hae-urakan-hoitokaudet db urakka-id))
                                       :valittu-hoitokausi valittu-hoitokausi}))
        rahavarausmuutos-summa (or (:tavoitehinnan-muutos (last muutos-rahavaraukset)) 0)
        tehtava-ja-maaramuutos-summa (if tehtava-ja-maaramuutokset
                                       (reduce + 0 (keep :tavoitehinnan_muutos tehtava-ja-maaramuutokset))
                                       0)
        taman-vuoden-muutokset-summa (+ (or aktiiviset-muutokset 0) (or tehtava-ja-maaramuutos-summa 0) (or rahavarausmuutos-summa 0))

        ;; 2025 vuodesta eteenpäin on käytössä vuosittaiset muutoset/pysyvät muutokset
        muutosvaikutus (if (>= 2024 urakan-alkuvuosi) 0 taman-vuoden-muutokset-summa)
        hoitovuoden-lopun-indeksikorjaamaton-tavoitehinta (+ (or (:tavoitehinta-oikaistu budjettitavoite-vuodelle) 0) muutosvaikutus)]
    ;; Joissakin tilanteissa saadaan kolme desimaalia, joka on euroissa hieman ongelmallista
    (bigdec (round2 2 hoitovuoden-lopun-indeksikorjaamaton-tavoitehinta))))

(defn maarita-hv-lopun-indeksikorjattu-tavoitehinta
  "Useimmat päätökset vaativat tietää hoitovuoden lopun tavoitehinnan.
  Sitä ei ole ikävä kyllä tallennettu kantaan valmiiksi.
  Tällä helpperillä määrittetään hoitovuoden lopun tavoitehinta, jossa on mukana indeksikorjauspäätöksen vaikutus, mikäli päätös on tehty.

  Budjettitavoite-vuodelle parametrin sisältämä tavoitehinta-oikaistu arvo sisältää mahdolliset tavoitehinnan oikaisut,
  joita on tehty 2024 ja sitä aemmin alkaneille urakoille."
  [db kayttaja hoitokauden-alkuvuosi valittu-hoitokausi urakka-id urakan-alkuvuosi budjettitavoite-vuodelle]
  (let [indeksikorjaamaton-tavoitehinta (maarita-hv-lopun-indeksikorjaamaton-tavoitehinta db kayttaja hoitokauden-alkuvuosi valittu-hoitokausi urakka-id urakan-alkuvuosi budjettitavoite-vuodelle)
        hoitokauden-lopun-indeksikorjaus (paatos-kyselyt/hae-hoitokauden-lopun-indeksikorjaus db {:urakkaid urakka-id :hoitokauden_alkuvuosi hoitokauden-alkuvuosi})
        hoitovuoden-lopun-tavoitehinta (+ indeksikorjaamaton-tavoitehinta (or hoitokauden-lopun-indeksikorjaus 0))]
    ;; Joissakin tilanteissa saadaan kolme desimaalia, joka on euroissa hieman ongelmallista
    (bigdec (round2 2 hoitovuoden-lopun-tavoitehinta))))

(defn hae-paatokset [db kayttaja urakkaid valittu-hoitovuosi budjettitavoite-vuodelle
                     toteutuneet-pisteet luvatut-pisteet toteutuneet-kustannukset urakan-parametrit urakan-tiedot
                     tehtava-ja-maaramuutos-summa rahavarausmuutos-summa]
  (let [;; Kootaan päätöksiä varten tarvittavat tiedot
        nyt-vuosi (pvm/vuosi (pvm/nyt))
        hoitovuosi-kesken? (pvm/valissa? (pvm/nyt) (pvm/hoitokauden-alkupvm valittu-hoitovuosi) (pvm/hoitokauden-loppupvm (inc valittu-hoitovuosi)))
        jarjestelman-asetukset (first (jarjestelma-kyselyt/hae-jarjestelman-asetukset db))
        indeksi (:indeksi urakan-tiedot)
        urakan-alkuvuosi (-> urakan-tiedot :alkupvm pvm/vuosi)
        urakan-loppuvuosi (dec (-> urakan-tiedot :loppupvm pvm/vuosi)) ;; Viimeisen hoitovuoden alkuvuosi käytännössä
        hoitovuosinro (pvm/paivamaara->mhu-hoitovuosi-nro (:alkupvm urakan-tiedot) (pvm/->pvm (str "1.10." valittu-hoitovuosi)))
        hoitokauden-alkupvm (pvm/hoitokauden-alkupvm valittu-hoitovuosi)
        hoitokauden-loppupvm (pvm/hoitokauden-loppupvm (inc valittu-hoitovuosi))
        valittu-hoitokausi [hoitokauden-alkupvm hoitokauden-loppupvm]
        mhu+urakka? (= "mhu+" (:sopimustyyppi urakan-tiedot))
        mhu-tyyppi (paatoskone/urakan-hoitotyyppi mhu+urakka?)
        tavoitehinta-vahvistettu? (:exists (first (budjettisuunnittelu-q/onko-kustannussuunnitelma-vahvistettu db
                                                    {:urakkaid urakkaid
                                                     :hoitovuosinro hoitovuosinro})))
        tavoitehinta-indeksikorjattu (:tavoitehinta-indeksikorjattu budjettitavoite-vuodelle)
        oikaistu-tavoitehinta (:tavoitehinta-oikaistu budjettitavoite-vuodelle)
        hoitovuoden-lopun-indeksikorjattu-tavoitehinta (maarita-hv-lopun-indeksikorjattu-tavoitehinta db kayttaja valittu-hoitovuosi valittu-hoitokausi urakkaid urakan-alkuvuosi budjettitavoite-vuodelle)
        hoitokauden-alun-tavoitehinta (valikatselmus-q/hae-hoitokauden-alun-indeksikorjattu-tavoitehinta db {:urakka-id urakkaid :hoitokauden-alkuvuosi valittu-hoitovuosi})
        oikaistu-kattohinta (:kattohinta-oikaistu budjettitavoite-vuodelle)
        hoitovuoden-lopun-kattohinta (:hoitovuoden-lopun-kattohinta budjettitavoite-vuodelle)
        tarjouksen-tavoitehinta (:tarjous-tavoitehinta budjettitavoite-vuodelle)

        muokkaa-kattohinta? (:muokkaa_kattohinta_kasin urakan-parametrit)
        kattohintakerroin (:hoitokauden_lopun_kattohinta_kerroin urakan-parametrit)
        lisaa-hoitokauden-lopun-indeksikorjaus (:lisaa_tavoitehintaan_hoitovuodenlopunindeksikorjaus urakan-parametrit)

        ;; Haetaan indeksikorjauksen vaatimat tavoitehinnan muutokset
        tavoitehinnan-oikaisut (valikatselmus-q/hae-tavoitehinnan-muutokset-hoitokaudelle db {:urakkaid urakkaid :hoitokauden_alkuvuosi valittu-hoitovuosi})
        tavoitehinnan-oikaisut-summa (apply + (map #(or (:summa %) 0) tavoitehinnan-oikaisut))
        ;; Haetaan pysyviin muutoksiin perustuvat tiedot
        aktiiviset-muutokset (:muutos-summa budjettitavoite-vuodelle)
        ;; Varmistetaan, että urakan muutosten hallinta on päällä
        taman-vuoden-muutokset-summa (if (:muutosten_hallinta urakan-parametrit)
                                       (+ (or aktiiviset-muutokset 0) (or tehtava-ja-maaramuutos-summa 0) (or rahavarausmuutos-summa 0))
                                       0)
        mahdolliset-paatokset (paatoskone/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi valittu-hoitovuosi)
        hv-lopun-tavoitehinta-ilman-indeksia (maarita-hv-lopun-indeksikorjaamaton-tavoitehinta db kayttaja valittu-hoitovuosi valittu-hoitokausi urakkaid urakan-alkuvuosi budjettitavoite-vuodelle)
        ;; Edellisen hoitovuoden syyskuun pisteluku - eli elokuu
        ;; ;; Vaiha alku vuosi, eli vantaa 2024 . pitää tulla elokuu 2024
        alkuperainen-pisteluku (:arvo (indeksipalvelu/hae-urakan-kuukauden-indeksiarvo db urakkaid valittu-hoitovuosi 8))
        hoitokauden-indeksikuukaudet (hae-hoitovuoden-indeksiluvut db urakkaid valittu-hoitovuosi)

        ;; Hoitokauden lopun indeksikorjaus
        hoitokauden-lopun-indeksikorjaus (paatos-kyselyt/hae-hoitokauden-lopun-indeksikorjaus db {:urakkaid urakkaid
                                                                                                  :hoitokauden_alkuvuosi valittu-hoitovuosi})

        ;; Hoidonjohtopalkkion suunniteltu määrä
        hjpalkkio (paatos-kyselyt/hae-budjetoitu-hoidonjohtopalkkio-hoitokaudelle db {:urakkaid urakkaid
                                                                                      :alkupvm (pvm/hoitokauden-alkupvm valittu-hoitovuosi)
                                                                                      :loppupvm (pvm/hoitokauden-loppupvm (inc valittu-hoitovuosi))})
        hoidonjohtopalkkio (:budjetoitu_summa_indeksikorjattu (first hjpalkkio))
        ;; Haetaan tietokantaan mahdollisesti tallennetut päätökset
        tietokanta-paatokset (paatos-kyselyt/hae-paatokset db mahdolliset-paatokset urakkaid valittu-hoitovuosi)

        ;; Valmistellaan päätökset ui:ta varten
        mahdolliset-paatokset (paatoskone/valmistele-lupauspaatokset db (:valikatselmus_validoinnit_kaytossa jarjestelman-asetukset) valittu-hoitovuosi urakkaid mahdolliset-paatokset toteutuneet-pisteet luvatut-pisteet tavoitehinta-indeksikorjattu tarjouksen-tavoitehinta indeksi)
        mahdolliset-paatokset (paatoskone/valmistele-tavoitehinnan-muutospaatos (:valikatselmus_validoinnit_kaytossa jarjestelman-asetukset) mahdolliset-paatokset oikaistu-tavoitehinta oikaistu-kattohinta muokkaa-kattohinta? valittu-hoitovuosi)
        mahdolliset-paatokset (paatoskone/valmistele-indeksikorjauspaatos (:valikatselmus_validoinnit_kaytossa jarjestelman-asetukset) mahdolliset-paatokset oikaistu-tavoitehinta tavoitehinnan-oikaisut taman-vuoden-muutokset-summa hoitokauden-indeksikuukaudet alkuperainen-pisteluku valittu-hoitovuosi tietokanta-paatokset tavoitehinta-vahvistettu? urakan-alkuvuosi)
        mahdolliset-paatokset (paatoskone/valmistele-hv-lopun-tavoite-ja-kattohinta (:valikatselmus_validoinnit_kaytossa jarjestelman-asetukset) urakan-alkuvuosi valittu-hoitovuosi mahdolliset-paatokset tavoitehinta-indeksikorjattu tavoitehinnan-oikaisut taman-vuoden-muutokset-summa hoitokauden-lopun-indeksikorjaus hoitovuoden-lopun-kattohinta kattohintakerroin lisaa-hoitokauden-lopun-indeksikorjaus tietokanta-paatokset mahdolliset-paatokset)
        mahdolliset-paatokset (paatoskone/valmistele-tavoitehinnan-alituspaatos db (:valikatselmus_validoinnit_kaytossa jarjestelman-asetukset) urakkaid mahdolliset-paatokset urakan-alkuvuosi urakan-loppuvuosi valittu-hoitovuosi hoitokauden-alun-tavoitehinta hoitovuoden-lopun-indeksikorjattu-tavoitehinta toteutuneet-kustannukset tietokanta-paatokset tavoitehinta-vahvistettu?)
        mahdolliset-paatokset (paatoskone/valmistele-tavoitehinnan-ylityspaatos db (:valikatselmus_validoinnit_kaytossa jarjestelman-asetukset) urakkaid mahdolliset-paatokset urakan-alkuvuosi urakan-loppuvuosi valittu-hoitovuosi hoitovuoden-lopun-indeksikorjattu-tavoitehinta hoitovuoden-lopun-kattohinta toteutuneet-kustannukset tietokanta-paatokset tavoitehinta-vahvistettu?)
        mahdolliset-paatokset (paatoskone/valmistele-kattohinnan-paatokset db (:valikatselmus_validoinnit_kaytossa jarjestelman-asetukset) urakkaid mahdolliset-paatokset hoitovuoden-lopun-kattohinta toteutuneet-kustannukset valittu-hoitovuosi urakan-alkuvuosi urakan-loppuvuosi tietokanta-paatokset tavoitehinta-vahvistettu?)
        mahdolliset-paatokset (paatoskone/valmistele-hoidonjohtopalkkionmuutospaatos (:valikatselmus_validoinnit_kaytossa jarjestelman-asetukset) valittu-hoitovuosi mahdolliset-paatokset hv-lopun-tavoitehinta-ilman-indeksia tarjouksen-tavoitehinta hoidonjohtopalkkio tietokanta-paatokset urakan-alkuvuosi)
        mahdolliset-paatokset (paatoskone/valmistele-raporttipaatos (:valikatselmus_validoinnit_kaytossa jarjestelman-asetukset) valittu-hoitovuosi mahdolliset-paatokset)

        ;; Keskeneräiselle tai tulevaisuuden hoitovuodelle näytetään vain yksi päätös - Tavoitehinnan muutospäätös.
        mahdolliset-paatokset (if (and (:valikatselmus_validoinnit_kaytossa jarjestelman-asetukset)
                                    (or (>= valittu-hoitovuosi nyt-vuosi)
                                      hoitovuosi-kesken?))
                                ;; Poistetaan kaikki muut päätökset, kuin tavoitehinnan muutospäätös
                                (filter #(when (= (:nimi %) "Tavoitehinnan muutokset") %) mahdolliset-paatokset)
                                ;; Palautetaan kaikki päätökset, jos ei ole hoitovuosi menossa tai jos validoinnit on poissa päältä
                                mahdolliset-paatokset)

        ;; Yhdistä päätökset listaksi. Tietokannasta haetut päätökset ovat tärkeydeltään tärkeämpiä, kuin päätöskoneelta saadut
        paatokset (paatoskone/yhdista-mapit mahdolliset-paatokset tietokanta-paatokset)]
    paatokset))

(defn hae-kustannukset-jarjestettyna [db urakkaid hoitovuosi hoitokauden-alkupvm hoitokauden-loppupvm]
  (let [kustannukset (kustannusten-seuranta-palvelu/hae-urakan-kustannusten-seuranta-paaryhmittain-ilman-validointia
                       db {:urakka-id urakkaid
                           :hoitokauden-alkuvuosi hoitovuosi
                           :alkupvm hoitokauden-alkupvm
                           :loppupvm hoitokauden-loppupvm})
        urakan-sopimustyyppi (:sopimustyyppi (first (q-urakat/hae-urakan-tiedot db urakkaid)))
        ;; Formatoidaan kustannukset ui:ta varten
        kustannukset-jarjestettyna (kustannusten-seuranta/jarjesta-tehtavat kustannukset urakan-sopimustyyppi)]
    kustannukset-jarjestettyna))

(defn hae-valikatselmuksen-tiedot-hoitovuodelle [db kayttaja {:keys [urakkaid hoitovuosi]}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-lupaukset kayttaja urakkaid)
  (let [urakan-tiedot (first (q-urakat/hae-urakan-tiedot db urakkaid))
        urakan-parametrit (first (q-urakat/hae-urakan-parametrit db {:urakkaid urakkaid}))
        vanha-urakka? (lupaus-domain/urakka-19-20? urakan-tiedot)
        hoitokauden-alkupvm (pvm/hoitokauden-alkupvm hoitovuosi)
        hoitokauden-loppupvm (pvm/hoitokauden-loppupvm (inc hoitovuosi))
        valittu-hoitokausi [hoitokauden-alkupvm hoitokauden-loppupvm]

        ;; 2019/2020 vuosille haetaan erilaiset lupausitiedot
        lupaus-parametrit {:urakka-id urakkaid
                           :valittu-hoitokausi valittu-hoitokausi
                           :nykyhetki (pvm/nyt)}
        lupaustiedot (if vanha-urakka?
                       (lupaus-palvelu/hae-kuukausittaiset-pisteet-hoitokaudelle db lupaus-parametrit)
                       (lupaus-palvelu/hae-urakan-lupaustiedot-hoitokaudelle db lupaus-parametrit))
        luvatut-pisteet (get-in lupaustiedot [:lupaus-sitoutuminen :pisteet])
        toteutuneet-pisteet (get-in lupaustiedot [:yhteenveto :pisteet :toteuma])

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
        kustannukset-jarjestettyna (hae-kustannukset-jarjestettyna db urakkaid hoitovuosi hoitokauden-alkupvm hoitokauden-loppupvm)
        budjettitavoite (budjettisuunnittelu-q/hae-budjettitavoite db {:urakka urakkaid})
        ;; Otetaan käytyn hoitovuoden budjetti
        budjettitavoite-vuodelle (some #(when (= (:hoitokauden-alkuvuosi %) hoitovuosi) %) budjettitavoite)
        ;; Kustannusten mukana ei tule tarvittavalla tasolla erotettuna bonuksia. Joten haetaan ne erikseen
        bonukset (valikatselmus-q/hae-bonukset db {:urakka-id urakkaid
                                                   :alkupvm hoitokauden-alkupvm
                                                   :loppupvm hoitokauden-loppupvm})
        ;; Kustannusten mukana ei tule tarvittavalla tasolla erotettuna sanktioita. Joten haetaan ne erikseen
        sanktiot (valikatselmus-q/hae-sanktiot db {:urakka-id urakkaid
                                                   :alkupvm hoitokauden-alkupvm
                                                   :loppupvm hoitokauden-loppupvm})
        toteutuneet-kustannukset (get-in kustannukset-jarjestettyna [:yhteensa :yht-toteutunut-summa])

        ;; Muutosten aiheuttamat muutokset tavoitehinnassa
        muutos-rahavaraukset (rahavaraus-kyselyt/muutosten-rahavaraukset db urakkaid hoitovuosi)
        ;; Hae kaikki tehtävä ja määrämuutokset - jos ne on urakalla käytössä
        ;; Määrämuutosten hakeminen on superhidasta, kun urakalla on paljon toteumia.
        ;; Joten vältetään sitä, jos mahdollista.
        tehtava-ja-maaramuutokset (when (:muutosten_hallinta urakan-parametrit)
                                    (muutos-palvelu/hae-tehtava-maaramuutokset db kayttaja
                                      {:urakka-id urakkaid
                                       :laskenta-automatiikka? true
                                       :hoitokaudet (hoitokaudet-vektorimuotoon (q-urakat/hae-urakan-hoitokaudet db urakkaid))
                                       :valittu-hoitokausi valittu-hoitokausi}))
        rahavarausmuutos-summa (or (:tavoitehinnan-muutos (last muutos-rahavaraukset)) 0)
        tehtava-ja-maaramuutos-summa (if tehtava-ja-maaramuutokset
                                       (reduce + 0 (keep :tavoitehinnan_muutos tehtava-ja-maaramuutokset))
                                       0)
        toteumiin-perustuvat-muutokset-yht (+ rahavarausmuutos-summa tehtava-ja-maaramuutos-summa)

        paatokset (hae-paatokset db kayttaja urakkaid hoitovuosi budjettitavoite-vuodelle toteutuneet-pisteet luvatut-pisteet
                    toteutuneet-kustannukset urakan-parametrit urakan-tiedot tehtava-ja-maaramuutos-summa rahavarausmuutos-summa)
        ;; Wrapataan paatoksen omien avainten alle, jotta käyttöliittymässä on mahdollista näyttää ne oikein
        paatokset (reduce (fn [v paatos]
                            ;; Täydennä viimeiset pakolliset tiedot
                            (let [paatos (merge
                                           (dissoc paatos :nakyvyys_alkaen :hoitotyyppi)
                                           {:urakkaid urakkaid
                                            :hoitokauden_alkuvuosi (pvm/vuosi hoitokauden-alkupvm)})]
                              (conj v {(paatoskone/nimi->avain (:nimi paatos)) paatos})))

                    [] paatokset)

        vastaus {:hoitokauden-alkuvuosi hoitovuosi
                 :urakan-parametrit urakan-parametrit
                 :yhteenveto {:lupaustiedot (dissoc lupaustiedot :lupausryhmat :lahtotiedot)
                              :kustannukset-yhteensa (:yhteensa kustannukset-jarjestettyna)
                              :kustannukset (:taulukon-rivit kustannukset-jarjestettyna)
                              :bonukset bonukset
                              :sanktiot sanktiot
                              :budjettitavoite budjettitavoite-vuodelle
                              :toteumiin-perustuvat-muutokset-yht toteumiin-perustuvat-muutokset-yht}
                 :paatokset paatokset
                 :tavoitehinnan-muutokset tavoitehinnan-muutokset
                 :kattohinnan-muutokset kattohinnan-muutokset}]
    vastaus))

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

      (if (::valikatselmus/kattohinnan-oikaisun-id oikaisu-specql)
        (do (valikatselmus-q/paivita-kattohinnan-oikaisu db oikaisu-specql)
          (valikatselmus-q/hae-kattohinnan-oikaisu db urakka-id hoitokauden-alkuvuosi))
        (valikatselmus-q/tee-kattohinnan-oikaisu db oikaisu-specql))

      ;; Hae välikatselmuksen tiedot
      (hae-valikatselmuksen-tiedot-hoitovuodelle db kayttaja {:urakkaid urakka-id :hoitovuosi hoitokauden-alkuvuosi}))))

(defn poista-kattohinnan-oikaisu [db kayttaja {hoitokauden-alkuvuosi ::valikatselmus/hoitokauden-alkuvuosi urakka-id ::urakka/id :as tiedot}]
  {:pre [(number? urakka-id) (pos-int? hoitokauden-alkuvuosi)]}
  (log/debug "poista-kattohinnan-oikaisu :: tiedot" (pr-str tiedot))
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-kulut-valikatselmus
    kayttaja
    urakka-id)
  (jdbc/with-db-transaction [db db]
    (let [urakka (first (q-urakat/hae-urakka db urakka-id))
          _ (tarkista-valikatselmusten-urakkatyyppi urakka :tavoitehinnan-oikaisu)]
      (valikatselmus-q/poista-kattohinnan-oikaisu db urakka-id hoitokauden-alkuvuosi kayttaja)
      (valikatselmus-q/hae-kattohinnan-oikaisu db urakka-id hoitokauden-alkuvuosi)
      ;; Hae välikatselmuksen tiedot
      (hae-valikatselmuksen-tiedot-hoitovuodelle db kayttaja {:urakkaid urakka-id :hoitovuosi hoitokauden-alkuvuosi}))))

;; Tavoitehinnan oikaisuja tehdään loppuvuodesta välikatselmuksessa.
;; Nämä summataan tai vähennetään alkuperäisestä tavoitehinnasta.
(defn tallenna-tavoitehinnan-oikaisu [db kayttaja tiedot]
  (log/debug "tallenna-tavoitehinnan-oikaisu :: tiedot" (pr-str tiedot))
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-valitavoitteet kayttaja (::urakka/id tiedot))
  (let [urakka-id (::urakka/id tiedot)
        urakka (first (q-urakat/hae-urakka db urakka-id))
        hoitokauden-alkuvuosi (::valikatselmus/hoitokauden-alkuvuosi tiedot)
        _ (do (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-kulut-valikatselmus
                kayttaja
                urakka-id)
            (tarkista-valikatselmusten-urakkatyyppi urakka :tavoitehinnan-oikaisu))

        uusi? (= 0 (::valikatselmus/oikaisun-id tiedot))
        tiedot (select-keys tiedot (columns ::valikatselmus/tavoitehinnan-oikaisu))
        oikaisu-specql (merge tiedot {::urakka/id urakka-id
                                      ::muokkaustiedot/luoja-id (:id kayttaja)
                                      ::muokkaustiedot/muokkaaja-id (:id kayttaja)
                                      ::muokkaustiedot/luotu (or (::muokkaustiedot/luotu tiedot) (pvm/nyt))
                                      ::muokkaustiedot/muokattu (pvm/nyt)
                                      ::valikatselmus/summa (bigdec (::valikatselmus/summa tiedot))
                                      ::valikatselmus/hoitokauden-alkuvuosi hoitokauden-alkuvuosi})

        oikaisu-specql (if uusi?
                         (dissoc oikaisu-specql ::valikatselmus/oikaisun-id)
                         oikaisu-specql)]

    (if (and
          (not uusi?)
          (::valikatselmus/oikaisun-id tiedot))
      (valikatselmus-q/paivita-oikaisu db oikaisu-specql)
      (valikatselmus-q/tee-oikaisu db oikaisu-specql))
    ;; Hae välikatselmuksen tiedot
    (hae-valikatselmuksen-tiedot-hoitovuodelle db kayttaja {:urakkaid urakka-id :hoitovuosi hoitokauden-alkuvuosi})))

(defn poista-tavoitehinnan-oikaisu [db kayttaja {::valikatselmus/keys [oikaisun-id] :as tiedot}]
  {:pre [(number? oikaisun-id)]}
  (log/debug "poista-tavoitehinnan-oikaisu :: tiedot" (pr-str tiedot))
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-valitavoitteet kayttaja (::urakka/id tiedot))
  (let [oikaisu (valikatselmus-q/hae-oikaisu db oikaisun-id)
        hoitokauden-alkuvuosi (::valikatselmus/hoitokauden-alkuvuosi oikaisu)
        urakka-id (::urakka/id oikaisu)
        urakka (first (q-urakat/hae-urakka db urakka-id))
        _ (do (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-kulut-valikatselmus
                kayttaja
                urakka-id)
            (tarkista-valikatselmusten-urakkatyyppi urakka :tavoitehinnan-oikaisu))]
    (valikatselmus-q/poista-oikaisu db tiedot kayttaja)
    ;; Hae välikatselmuksen tiedot
    (hae-valikatselmuksen-tiedot-hoitovuodelle db kayttaja {:urakkaid urakka-id :hoitovuosi hoitokauden-alkuvuosi})))

(defn tee-lupauspaatos [db kayttaja paatos]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-kulut-valikatselmus kayttaja (:urakkaid paatos))
  (log/info "palvelu :: tee-lupauspaatos :: paatos" (pr-str paatos))
  (jdbc/with-db-transaction [db db]
    (let [validaatio #{}
          urakka-id (:urakkaid paatos)
          hoitokauden-alkuvuosi (:hoitokauden_alkuvuosi paatos)
          ;; Verrataan tietokannan tavoitehintaa saatuun tavoitehintaan
          tavoitehinta (valikatselmus-q/hae-hoitokauden-alun-indeksikorjattu-tavoitehinta db {:urakka-id urakka-id
                                                                                              :hoitokauden-alkuvuosi hoitokauden-alkuvuosi})
          validaatio (if-not (= (konversio/konvertoi->int tavoitehinta) (konversio/konvertoi->int (:tavoitehinta paatos)))
                       (conj validaatio (str "Tavoitehinta ei täsmää suunnitelman kanssa. Suunniteltu tavoitehinta:" tavoitehinta "€. Päätöksen mukainen tavoitehinta: " (:tavoitehinta paatos) " €"))
                       validaatio)
          ;; Jos ollaan tekemässä lupauspäätöstä, josta tulee bonusta
          erilliskustannus_id (when (and (= "bonus" (:tyyppi paatos)) (:lupausbonus paatos))
                                (paatos-apurit/tallenna-lupausbonus db paatos kayttaja))
          ;; Tai jos tulee sakkoja, niin tehdään sanktio
          sanktio_id (when (and (= "sanktio" (:tyyppi paatos)) (:lupaussanktio paatos))
                       (paatos-apurit/tallenna-lupaussanktio db paatos kayttaja))
          paatos (-> paatos
                   (assoc :erilliskustannus_id erilliskustannus_id)
                   (assoc :sanktio_id sanktio_id))
          _ (if (seq validaatio)
              (heita-virhe (str "Virheellinen päätös: " (string/join ", " validaatio)))
              (paatos-kyselyt/tee-lupauspaatos db paatos))]
      ;; Palautetaan koko välikatselmus
      (hae-valikatselmuksen-tiedot-hoitovuodelle db kayttaja {:urakkaid (:urakkaid paatos) :hoitovuosi (:hoitokauden_alkuvuosi paatos)}))))

(defn poista-lupauspaatos [db kayttaja paatos]
  (log/debug "poista-lupauspaatos :: paatos" (pr-str paatos))
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-kulut-valikatselmus kayttaja (:urakkaid paatos))

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
      ;; Palautetaan koko välikatselmus
      (hae-valikatselmuksen-tiedot-hoitovuodelle db kayttaja {:urakkaid (:urakkaid paatos) :hoitovuosi (:hoitokauden_alkuvuosi paatos)}))))

(defn tee-tavoitehinnan-muutospaatos [db kayttaja paatos]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-kulut-valikatselmus kayttaja (:urakkaid paatos))
  (log/info "tee-tavoitehinnan-muutospaatos :: paatos" (pr-str paatos))
  (jdbc/with-db-transaction [db db]
    (let [validaatio #{}
          urakka-id (:urakkaid paatos)
          hoitokauden-alkuvuosi (:hoitokauden_alkuvuosi paatos)
          ;; Varmistetaan, että päätöstä ei ole vielä tehty
          olemassaoleva-paatos (first (paatos-kyselyt/hae-tavoitehinnan-muutos-paatokset db {:urakkaid urakka-id
                                                                                             :hoitokauden_alkuvuosi hoitokauden-alkuvuosi}))
          validaatio (if olemassaoleva-paatos
                       (conj validaatio (str "Päätös on jo tehty tälle hoitokaudelle. Päivitä selain."))
                       validaatio)

          ;; Verrataan tietokannan tavoitehintaa saatuun tavoitehintaan
          tavoitehinta (valikatselmus-q/hae-oikaistu-tavoitehinta db {:urakka-id urakka-id
                                                                      :hoitokauden-alkuvuosi hoitokauden-alkuvuosi})
          validaatio (if-not (= (konversio/konvertoi->int tavoitehinta) (konversio/konvertoi->int (:tavoitehinta paatos)))
                       (conj validaatio (str "Tavoitehinta ei täsmää suunnitelman kanssa. Suunniteltu tavoitehinta:" tavoitehinta "€. Päätöksen mukainen tavoitehinta: " (:tavoitehinta paatos) " €"))
                       validaatio)
          kattohinta (valikatselmus-q/hae-oikaistu-kattohinta db {:urakka-id urakka-id
                                                                  :hoitokauden-alkuvuosi hoitokauden-alkuvuosi})
          validaatio (if-not (= (konversio/konvertoi->int kattohinta) (konversio/konvertoi->int (:kattohinta paatos)))
                       (conj validaatio (str "Kattohinta ei täsmää suunnitelman kanssa. Suunniteltu kattohinta:" kattohinta " €. Päätöksen mukainen kattohinta: " (:kattohinta paatos) " €"))
                       validaatio)
          _ (if (seq validaatio)
              (heita-virhe (str "Virheellinen päätös: " (string/join ", " validaatio)))
              (paatos-kyselyt/tee-tavoitehinnan-muutospaatos db paatos (:id kayttaja)))]

      ;; Hae välikatselmuksen tiedot
      (hae-valikatselmuksen-tiedot-hoitovuodelle db kayttaja {:urakkaid (:urakkaid paatos) :hoitovuosi (:hoitokauden_alkuvuosi paatos)}))))

(defn poista-tavoitehinnan-muutospaatos [db kayttaja paatos]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-kulut-valikatselmus kayttaja (:urakkaid paatos))
  (log/debug "poista-tavoitehinnan-muutospaatos :: paatos" (pr-str paatos))
  ;; Poista päätös
  (paatos-kyselyt/poista-tavoitehinnan-muutospaatos db (:urakkaid paatos) (:id kayttaja) (:id paatos))
  ;; Hae välikatselmuksen tiedot
  (hae-valikatselmuksen-tiedot-hoitovuodelle db kayttaja {:urakkaid (:urakkaid paatos) :hoitovuosi (:hoitokauden_alkuvuosi paatos)}))

(defn tee-tavoitehinnan-alituspaatos [db kayttaja paatos]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-kulut-valikatselmus kayttaja (:urakkaid paatos))
  (log/info "tee-tavoitehinnan-alituspaatos :: paatos" (pr-str paatos))
  (jdbc/with-db-transaction [db db]
    (let [validaatio #{}
          urakka-id (:urakkaid paatos)
          urakka (first (q-urakat/hae-urakka db urakka-id))
          urakan-alkuvuosi (-> (:alkupvm urakka) pvm/vuosi)
          hoitokauden-alkuvuosi (:hoitokauden_alkuvuosi paatos)
          hoitokauden-alkupvm (pvm/hoitokauden-alkupvm hoitokauden-alkuvuosi)
          hoitokauden-loppupvm (pvm/hoitokauden-loppupvm (inc hoitokauden-alkuvuosi))
          valittu-hoitokausi [hoitokauden-alkupvm hoitokauden-loppupvm]
          viimeinen-hoitovuosi? (= hoitokauden-alkuvuosi (dec (-> urakka :loppupvm pvm/vuosi)))
          budjettitavoite-vuodelle (budjettisuunnittelu-q/budjettitavoite-vuodelle db urakka-id hoitokauden-alkuvuosi)

          ;; Verrataan tietokannan hoitokauden_lopun_tavoitehinta saatuun hoitokauden_lopun_tavoitehintaan
          hoitokauden_lopun_tavoitehinta (maarita-hv-lopun-indeksikorjattu-tavoitehinta db kayttaja hoitokauden-alkuvuosi valittu-hoitokausi urakka-id urakan-alkuvuosi budjettitavoite-vuodelle)
          validaatio (if-not (= (konversio/konvertoi->int hoitokauden_lopun_tavoitehinta) (konversio/konvertoi->int (:hoitokauden_lopun_tavoitehinta paatos)))
                       (conj validaatio (str "Päätöksen hoitokauden lopun tavoitehinta ei täsmää suunnitelman kanssa. Suunniteltu hoitokauden lopun tavoitehinta: " hoitokauden_lopun_tavoitehinta "€.
                       Päätöksessä annettu hoitokauden lopun tavoitehinta: " (:hoitokauden_lopun_tavoitehinta paatos) " €"))
                       validaatio)

          ;; Verrataan tietokannan hoitokauden alun tavoitehintaa saatuun hoitokauden alun tavoitehintaan
          hoitokauden-alun-tavoitehinta (valikatselmus-q/hae-hoitokauden-alun-indeksikorjattu-tavoitehinta db {:urakka-id urakka-id :hoitokauden-alkuvuosi hoitokauden-alkuvuosi})
          validaatio (if-not (= (konversio/konvertoi->int hoitokauden-alun-tavoitehinta) (konversio/konvertoi->int (:hoitokauden_alun_tavoitehinta paatos)))
                       (conj validaatio (str "Päätöksen tavoitehinta ei täsmää suunnitelman kanssa. Suunniteltu hoitokauden alun tavoitehinta:" hoitokauden-alun-tavoitehinta "€.
                       Päätöksen annettu hoitokauden alun tavoitehinta: " (:hoitokauden_alun_tavoitehinta paatos) " €"))
                       validaatio)
          ;; Jos validointi on kunnossa, niin luodaan tavoitepalkkiokulu
          kulu_id (when-not (seq validaatio)
                    ;; Viimeisenä hoitovuotena tehdään tilaajalle kululasku koko alituksesta. Muuten vain tavoitepalkkio
                    (if-not viimeinen-hoitovuosi?
                      (paatos-apurit/tallenna-kulu db paatos kayttaja :tavoitehinnan-alitus (:tavoitepalkkio paatos))
                      (paatos-apurit/tallenna-kulu db paatos kayttaja :tavoitehinnan-alitus (:alituksen_maara paatos))))
          paatos (assoc paatos :kulu_id kulu_id)
          _ (if (seq validaatio)
              (heita-virhe (str "Virheellinen päätös: " (string/join ", " validaatio)))
              (paatos-kyselyt/tee-tavoitehinnan-alituspaatos db paatos))]

      ;; Hae välikatselmuksen tiedot
      (hae-valikatselmuksen-tiedot-hoitovuodelle db kayttaja {:urakkaid (:urakkaid paatos) :hoitovuosi (:hoitokauden_alkuvuosi paatos)}))))

(defn poista-tavoitehinnan-alituspaatos [db kayttaja paatos]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-kulut-valikatselmus kayttaja (:urakkaid paatos))
  (log/debug "poista-tavoitehinnan-alituspaatos :: paatos" (pr-str paatos))
  (jdbc/with-db-transaction [db db]
    (let [;; Jos päätöksellä on kulu, niin poisteatan se samalla
          _ (when (:kulu_id paatos)
              (kulut-palvelu/poista-kulu-tietokannasta db kayttaja
                {:urakka-id (:urakkaid paatos) :id (:kulu_id paatos)}))

          _ (paatos-kyselyt/poista-tavoitehinnan-alituspaatos db (:urakkaid paatos) (:id kayttaja) (:id paatos))]

      ;; Hae välikatselmuksen tiedot
      (hae-valikatselmuksen-tiedot-hoitovuodelle db kayttaja {:urakkaid (:urakkaid paatos) :hoitovuosi (:hoitokauden_alkuvuosi paatos)}))))

(defn tee-tavoitehinnan-ylityspaatos [db kayttaja paatos]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-kulut-valikatselmus kayttaja (:urakkaid paatos))
  (log/debug "tee-tavoitehinnan-ylityspaatos :: paatos" (pr-str paatos))
  (jdbc/with-db-transaction [db db]
    (let [validaatio #{}
          urakka-id (:urakkaid paatos)
          urakan-tiedot (first (q-urakat/hae-urakka db urakka-id))
          urakan-alkuvuosi (-> urakan-tiedot (get :alkupvm) pvm/vuosi)
          hoitokauden-alkuvuosi (:hoitokauden_alkuvuosi paatos)
          hoitokauden-alkupvm (pvm/hoitokauden-alkupvm hoitokauden-alkuvuosi)
          hoitokauden-loppupvm (pvm/hoitokauden-loppupvm (inc hoitokauden-alkuvuosi))
          valittu-hoitokausi [hoitokauden-alkupvm hoitokauden-loppupvm]
          ;; Varmistetaan, että päätöstä ei ole vielä tehty
          olemassaoleva-paatos (first (paatos-kyselyt/hae-tavoitehinnnan-ylitys-paatokset db {:urakkaid urakka-id
                                                                                              :hoitokauden_alkuvuosi hoitokauden-alkuvuosi}))
          validaatio (if olemassaoleva-paatos
                       (conj validaatio (str "Päätös on jo tehty tälle hoitokaudelle. Päivitä selain."))
                       validaatio)

          ;; Verrataan tietokannan tavoitehintaa saatuun tavoitehintaan
          koko-budjettitavoite (budjettisuunnittelu-q/hae-budjettitavoite db {:urakka urakka-id})
          ;; Valitaan tämän käytetyn hoitovuoden budjettitavoite
          budjettitavoite-vuodelle (some #(when (= (:hoitokauden-alkuvuosi %) hoitokauden-alkuvuosi) %) koko-budjettitavoite)

          hoitovuoden-lopun-tavoitehinta (maarita-hv-lopun-indeksikorjattu-tavoitehinta db kayttaja hoitokauden-alkuvuosi valittu-hoitokausi urakka-id urakan-alkuvuosi budjettitavoite-vuodelle)
          validaatio (if-not (= (konversio/konvertoi->int hoitovuoden-lopun-tavoitehinta) (konversio/konvertoi->int (:tavoitehinta paatos)))
                       (conj validaatio (str "Tavoitehinta ei täsmää suunnitelman kanssa. Hoitovuoden lopun tavoitehinta: " hoitovuoden-lopun-tavoitehinta " €. Päätöksen mukainen tavoitehinta: " (:tavoitehinta paatos) " €"))
                       validaatio)

          ;; Jos validointi on kunnossa, niin luodaan tavoitehinnan ylityskulu - jonka maksaa urakoitsija
          kulu_id (when-not (seq validaatio)
                    (paatos-apurit/tallenna-kulu db paatos kayttaja :tavoitehinnan-ylitys (:urakoitsija_maksaa paatos)))
          paatos (assoc paatos :kulu_id kulu_id)
          _ (if (seq validaatio)
              (heita-virhe (str "Virheellinen päätös: " (string/join ", " validaatio)))
              (paatos-kyselyt/tee-tavoitehinnan-ylityspaatos db paatos))]

      ;; Hae välikatselmuksen tiedot
      (hae-valikatselmuksen-tiedot-hoitovuodelle db kayttaja {:urakkaid (:urakkaid paatos) :hoitovuosi (:hoitokauden_alkuvuosi paatos)}))))

(defn poista-tavoitehinnan-ylityspaatos [db kayttaja paatos]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-kulut-valikatselmus kayttaja (:urakkaid paatos))
  (log/debug "poista-tavoitehinnan-ylityspaatos :: paatos" (pr-str paatos))
  (jdbc/with-db-transaction [db db]
    (let [;; Jos päätöksellä on kulu, niin poisteatan se samalla
          _ (when (:kulu_id paatos)
              (kulut-palvelu/poista-kulu-tietokannasta db kayttaja
                {:urakka-id (:urakkaid paatos) :id (:kulu_id paatos)}))
          _ (paatos-kyselyt/poista-tavoitehinnan-ylityspaatos db (:urakkaid paatos) (:id kayttaja) (:id paatos))]
      ;; Hae välikatselmuksen tiedot
      (hae-valikatselmuksen-tiedot-hoitovuodelle db kayttaja {:urakkaid (:urakkaid paatos) :hoitovuosi (:hoitokauden_alkuvuosi paatos)}))))

(defn tee-kattohinnan-ylityspaatos [db kayttaja paatos]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-kulut-valikatselmus kayttaja (:urakkaid paatos))
  (log/debug "tee-kattohinnan-ylityspaatos :: paatos" (pr-str paatos))
  (jdbc/with-db-transaction [db db]
    (let [validaatio #{}
          urakka-id (:urakkaid paatos)
          urakan-tiedot (first (q-urakat/hae-urakan-tiedot db urakka-id))
          urakan-loppuvuoden-alkuvuosi (dec (-> urakan-tiedot :loppupvm pvm/vuosi)) ;; Viimeisen hoitovuoden alkuvuosi käytännössä
          urakan-parametrit (first (q-urakat/hae-urakan-parametrit db urakka-id))
          hoitokauden-alkuvuosi (:hoitokauden_alkuvuosi paatos)

          ;; Varmistetaan, että päätöstä ei ole vielä tehty
          olemassaoleva-paatos (first (paatos-kyselyt/hae-kattohinta-paatokset db {:urakkaid urakka-id
                                                                                   :hoitokauden_alkuvuosi hoitokauden-alkuvuosi}))
          validaatio (if olemassaoleva-paatos
                       (conj validaatio (str "Päätös on jo tehty tälle hoitokaudelle. Päivitä selain."))
                       validaatio)

          ;; Verrataan tietokannan kattohintaa saatuun kattohintaan
          koko-budjettitavoite (budjettisuunnittelu-q/hae-budjettitavoite db {:urakka urakka-id})
          ;; Valitaan tämän käytetyn hoitovuoden budjettitavoite
          budjettitavoite-vuodelle (some #(when (= (:hoitokauden-alkuvuosi %) hoitokauden-alkuvuosi) %) koko-budjettitavoite)
          hoitovuoden-lopun-kattohinta (:hoitovuoden-lopun-kattohinta budjettitavoite-vuodelle)

          validaatio (if (> (:siirrettava_maara paatos) (round2 2 (:ylityksen_maara paatos)))
                       (conj validaatio (str "Siirrettävä määrä ylittää maksimiarvon."))
                       validaatio)

          validaatio (if (> (:urakoitsija_maksaa paatos) (:ylityksen_maara paatos))
                       (conj validaatio (str "Urakoitsijan maksu ylittää maksimiarvon."))
                       validaatio)

          validaatio (if (> (+ (:urakoitsija_maksaa paatos) (:siirrettava_maara paatos)) (:ylityksen_maara paatos))
                       (conj validaatio (str "Urakoitsijan ja siirrettävä määrä ylittää maksimiarvon."))
                       validaatio)

          validaatio (if-not (= (konversio/konvertoi->int hoitovuoden-lopun-kattohinta) (konversio/konvertoi->int (:kattohinta paatos)))
                       (conj validaatio (str "Kattohinta ei täsmää suunnitelman kanssa. Hoitovuoden lopun kattohinta:" hoitovuoden-lopun-kattohinta " €. Päätöksen mukainen kattohinta: " (:kattohinta paatos) " €"))
                       validaatio)

          ;; Validoi siirto
          validaatio (if (and (:siirtorajoitus_prosentti urakan-parametrit) (> (:siirrettava_maara paatos) (:maksimi_siirrettava_maara paatos)))
                       (conj validaatio (str "Siirron rajoitus ylitetty. Maksimi siirto voi olla " (:siirtorajoitus_prosentti urakan-parametrit) " kattohinnasta."))
                       validaatio)

          ;; Validoi siirto viimeisenä hoitovuotena
          validaatio (if (and (= hoitokauden-alkuvuosi urakan-loppuvuoden-alkuvuosi) (> (:siirrettava_maara paatos) 0))
                       (conj validaatio (str "Viimeisenä hoitovuodena ei voida siirtää kuluja seuraavalle vuodelle. Poista siirron osuus."))
                       validaatio)

          ;; Jos validointi on kunnossa, niin luodaan kattohinnan ylityskulu - jonka maksaa urakoitsija
          kulu_id (when-not (seq validaatio)
                    (paatos-apurit/tallenna-kulu db paatos kayttaja :kattohinnan-ylitys (:urakoitsija_maksaa paatos)))
          paatos (assoc paatos :kulu_id kulu_id)
          vastaus (if (seq validaatio)
                    (heita-virhe (str (string/join ", " validaatio)))
                    (do
                      (paatos-kyselyt/tee-kattohinnan-ylityspaatos db paatos)
                      ;; Hae välikatselmuksen tiedot
                      (hae-valikatselmuksen-tiedot-hoitovuodelle db kayttaja {:urakkaid (:urakkaid paatos) :hoitovuosi (:hoitokauden_alkuvuosi paatos)})))]
      vastaus)))

(defn poista-kattohinnan-ylityspaatos [db kayttaja paatos]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-kulut-valikatselmus kayttaja (:urakkaid paatos))
  (log/debug "poista-kattohinnan-ylityspaatos :: paatos" (pr-str paatos))
  (jdbc/with-db-transaction [db db]
    (let [;; Jos päätöksellä on kulu, niin poisteatan se samalla
          _ (when (:kulu_id paatos)
              (kulut-palvelu/poista-kulu-tietokannasta db kayttaja
                {:urakka-id (:urakkaid paatos) :id (:kulu_id paatos)}))
          _ (paatos-kyselyt/poista-kattohinnan-ylityspaatos db (:urakkaid paatos) (:id kayttaja) (:id paatos))]

      ;; Hae välikatselmuksen tiedot
      (hae-valikatselmuksen-tiedot-hoitovuodelle db kayttaja {:urakkaid (:urakkaid paatos) :hoitovuosi (:hoitokauden_alkuvuosi paatos)}))))

(defn tee-indeksikorjauspaatos [db kayttaja paatos]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-kulut-valikatselmus kayttaja (:urakkaid paatos))
  (log/debug "tee-indeksikorjauspaatos :: paatos" (pr-str paatos))
  (jdbc/with-db-transaction [db db]
    (let [validaatio #{}
          urakka-id (:urakkaid paatos)
          urakan-tiedot (first (q-urakat/hae-urakan-tiedot db urakka-id))
          urakan-alkuvuosi (-> urakan-tiedot :alkupvm pvm/vuosi)
          hoitokauden-alkuvuosi (:hoitokauden_alkuvuosi paatos)
          hoitokauden-alkupvm (pvm/hoitokauden-alkupvm hoitokauden-alkuvuosi)
          hoitokauden-loppupvm (pvm/hoitokauden-loppupvm (inc hoitokauden-alkuvuosi))
          valittu-hoitokausi [hoitokauden-alkupvm hoitokauden-loppupvm]

          ;; Varmista, että päätöstä ei ole vielä tehty
          olemassaoleva-paatos (first (paatos-kyselyt/hae-indeksikorjauspaatokset db {:urakkaid urakka-id
                                                                                      :hoitokauden_alkuvuosi hoitokauden-alkuvuosi}))
          validaatio (if olemassaoleva-paatos
                       (conj validaatio (str "Päätös on jo tehty tälle hoitokaudelle. Päivitä selain."))
                       validaatio)

          ;; Verrataan tietokannan tavoitehintaa saatuun tavoitehintaan - tässä tavoitehinnassa ei ole tämän vuoden aktiivisia pysyviä muutoksia, mutta menneet pysyvät muutokset on.
          hk-alun-indkorj-tavoitehinta (valikatselmus-q/hae-hoitokauden-alun-indeksikorjattu-tavoitehinta db {:urakka-id urakka-id
                                                                                                              :hoitokauden-alkuvuosi hoitokauden-alkuvuosi})

          validaatio (if-not (= (konversio/konvertoi->int hk-alun-indkorj-tavoitehinta) (konversio/konvertoi->int (:hv_alun_indkorj_tavoitehinta paatos)))
                       (conj validaatio
                         (str "Hoitovuoden alun indeksikorjattu tavoitehinta ei täsmää suunnitelman kanssa. Suunniteltu hoitokauden alun indeksikorjattu tavoitehinta: " hk-alun-indkorj-tavoitehinta "€.
                       Päätöksen mukainen tavoitehinta: " (:hv_alun_indkorj_tavoitehinta paatos) " €"))
                       validaatio)

          ;; Lasketaan hoitovuoden lopun tavoitehinta ennen indeksikorjausta
          koko-budjettitavoite (budjettisuunnittelu-q/hae-budjettitavoite db {:urakka urakka-id})
          ;; Valitaan tämän käytetyn hoitovuoden budjettitavoite
          budjettitavoite-vuodelle (some #(when (= (:hoitokauden-alkuvuosi %) hoitokauden-alkuvuosi) %) koko-budjettitavoite)
          ;; Voidaan hakea indeksikorjattu tavoitehinta, koska indeksikorjauspäätöstä ei ole tässä vaiheessa vielä tallennettu, joten sitä ei löydy
          hv-lopun-indeksikorjaamaton-tavoitehinta (maarita-hv-lopun-indeksikorjaamaton-tavoitehinta db kayttaja hoitokauden-alkuvuosi valittu-hoitokausi urakka-id urakan-alkuvuosi budjettitavoite-vuodelle)

          validaatio (if-not (= (konversio/konvertoi->int hv-lopun-indeksikorjaamaton-tavoitehinta) (konversio/konvertoi->int (:hv_lopun_tavoitehinta_ennen_indkorj paatos)))
                       (conj validaatio (str "Hoitovuoden lopun tavoitehinta ennen indeksikorjausta ei täsmää suunnitelman kanssa.
                       Suunniteltu hoitovuoden lopun indeksikorjaamaton tavoitehinta: " hv-lopun-indeksikorjaamaton-tavoitehinta "€.
                       Päätöksen mukainen hoitovuoden lopun tavoitehinta ennen indeksikorjausta: " (:hv_lopun_tavoitehinta_ennen_indkorj paatos) " €"))
                       validaatio)

          _ (if (seq validaatio)
              (heita-virhe (str "Virheellinen päätös: " (string/join ", " validaatio)))
              (paatos-kyselyt/tee-indeksikorjauspaatos db paatos))]
      ;; Hae välikatselmuksen tiedot
      (hae-valikatselmuksen-tiedot-hoitovuodelle db kayttaja {:urakkaid (:urakkaid paatos) :hoitovuosi (:hoitokauden_alkuvuosi paatos)}))))

(defn poista-indeksikorjauspaatos [db kayttaja paatos]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-kulut-valikatselmus kayttaja (:urakkaid paatos))
  (log/debug "poista-indeksikorjauspaatos :: paatos" (pr-str paatos))
  (jdbc/with-db-transaction [db db]
    (let [_ (paatos-kyselyt/poista-indeksikorjauspaatos db (:urakkaid paatos) (:id kayttaja) (:id paatos))]

      ;; Hae välikatselmuksen tiedot
      (hae-valikatselmuksen-tiedot-hoitovuodelle db kayttaja {:urakkaid (:urakkaid paatos) :hoitovuosi (:hoitokauden_alkuvuosi paatos)}))))

(defn tee-hv-lopun-tavoite-ja-kattohintapaatos [db kayttaja paatos]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-kulut-valikatselmus kayttaja (:urakkaid paatos))
  (log/debug "tee-hv-lopun-tavoite-ja-kattohintapaatos :: paatos" (pr-str paatos))
  (jdbc/with-db-transaction [db db]
    (let [validaatio #{}
          urakka-id (:urakkaid paatos)
          urakan-tiedot (first (q-urakat/hae-urakan-tiedot db urakka-id))
          urakan-alkuvuosi (-> urakan-tiedot :alkupvm pvm/vuosi)
          hoitokauden-alkuvuosi (:hoitokauden_alkuvuosi paatos)
          hoitokauden-alkupvm (pvm/hoitokauden-alkupvm hoitokauden-alkuvuosi)
          hoitokauden-loppupvm (pvm/hoitokauden-loppupvm (inc hoitokauden-alkuvuosi))
          valittu-hoitokausi [hoitokauden-alkupvm hoitokauden-loppupvm]

          ;; Varmista, että päätöstä ei ole vielä tehty
          olemassaoleva-paatos (first (paatos-kyselyt/hae-hoitokauden-lopun-hinta-paatokset db {:urakkaid urakka-id
                                                                                                :hoitokauden_alkuvuosi hoitokauden-alkuvuosi}))
          validaatio (if olemassaoleva-paatos
                       (conj validaatio (str "Päätös on jo tehty tälle hoitokaudelle. Päivitä selain."))
                       validaatio)

          ;; Verrataan tietokannan tavoitehintaa saatuun tavoitehintaan
          koko-budjettitavoite (budjettisuunnittelu-q/hae-budjettitavoite db {:urakka urakka-id})
          ;; Valitaan tämän käytetyn hoitovuoden budjettitavoite
          budjettitavoite-vuodelle (some #(when (= (:hoitokauden-alkuvuosi %) hoitokauden-alkuvuosi) %) koko-budjettitavoite)

          hoitovuoden-lopun-tavoitehinta (maarita-hv-lopun-indeksikorjattu-tavoitehinta db kayttaja hoitokauden-alkuvuosi valittu-hoitokausi urakka-id urakan-alkuvuosi budjettitavoite-vuodelle)
          validaatio (if-not (= (konversio/konvertoi->int hoitovuoden-lopun-tavoitehinta) (konversio/konvertoi->int (:tavoitehinta_jalkeen paatos)))
                       (conj validaatio (str "Hoitovuoden lopun tavoitehinta ei täsmää suunnitelman kanssa. Hoitovuoden lopun tavoitehinta: " hoitovuoden-lopun-tavoitehinta " €. Päätöksen mukainen tavoitehinta: " (:tavoitehinta_jalkeen paatos) " €"))
                       validaatio)
          hoitovuoden-lopun-kattohinta (:hoitovuoden-lopun-kattohinta budjettitavoite-vuodelle)
          validaatio (if-not (= (konversio/konvertoi->int hoitovuoden-lopun-kattohinta) (konversio/konvertoi->int (:kattohinta paatos)))
                       (conj validaatio (str "Kattohinta ei täsmää suunnitelman kanssa. Hoitovuoden lopun kattohinta: " hoitovuoden-lopun-kattohinta " €. Päätöksen mukainen kattohinta: " (:kattohinta paatos) " €"))
                       validaatio)

          _ (if (seq validaatio)
              (heita-virhe (str "Virheellinen päätös: " (string/join ", " validaatio)))
              (paatos-kyselyt/tee-hoitokauden-lopun-hintapaatos db paatos))

          ;; Laske lopullinen lupausten kustannusennuste kun välikatselmus on tehty
          _ (let [toteutunut-tavoitehinta (:tavoitehinta_jalkeen paatos)
                  kustannukset-jarjestettyna (hae-kustannukset-jarjestettyna
                                               db urakka-id hoitokauden-alkuvuosi
                                               (pvm/hoitokauden-alkupvm hoitokauden-alkuvuosi)
                                               (pvm/hoitokauden-loppupvm (inc hoitokauden-alkuvuosi)))
                  toteutuneet-kustannukset (get-in kustannukset-jarjestettyna [:yhteensa :yht-toteutunut-summa])
                  paatos-pvm (pvm/nyt)
                  user-id (:id kayttaja)]
              (lupaus-palvelu/laske-lopullinen-kustannusennuste!
                db urakka-id hoitokauden-alkuvuosi toteutunut-tavoitehinta
                toteutuneet-kustannukset paatos-pvm user-id))]
      ;; Hae välikatselmuksen tiedot
      (hae-valikatselmuksen-tiedot-hoitovuodelle db kayttaja {:urakkaid (:urakkaid paatos) :hoitovuosi (:hoitokauden_alkuvuosi paatos)}))))


(defn poista-hoitovuoden-lopun-hintapaatos [db kayttaja paatos]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-kulut-valikatselmus kayttaja (:urakkaid paatos))
  (log/debug "poista-hoitovuoden-lopun-hintapaatos :: paatos" (pr-str paatos))
  (jdbc/with-db-transaction [db db]
    (let [_ (paatos-kyselyt/poista-hoitovuoden-lopun-hintapaatos db (:urakkaid paatos) (:id kayttaja) (:id paatos))]

      ;; Hae välikatselmuksen tiedot
      (hae-valikatselmuksen-tiedot-hoitovuodelle db kayttaja {:urakkaid (:urakkaid paatos) :hoitovuosi (:hoitokauden_alkuvuosi paatos)}))))

(defn tee-hoidonjohtopalkkion-muutospaatos [db kayttaja paatos]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-kulut-valikatselmus kayttaja (:urakkaid paatos))
  (log/info "valikatselmus :: tee-hoidonjohtopalkkion-muutospaatos :: paatos" (pr-str paatos))
  (jdbc/with-db-transaction [db db]
    (let [validaatio #{}
          urakkaid (:urakkaid paatos)
          urakan-tiedot (first (q-urakat/hae-urakan-tiedot db urakkaid))
          urakan-alkuvuosi (-> urakan-tiedot :alkupvm pvm/vuosi)
          hoitokauden-alkuvuosi (:hoitokauden_alkuvuosi paatos)
          hoitokauden-alkupvm (pvm/hoitokauden-alkupvm hoitokauden-alkuvuosi)
          hoitokauden-loppupvm (pvm/hoitokauden-loppupvm (inc hoitokauden-alkuvuosi))
          valittu-hoitokausi [hoitokauden-alkupvm hoitokauden-loppupvm]
          budjettitavoite (budjettisuunnittelu-q/hae-budjettitavoite db {:urakka urakkaid})
          ;; Otetaan käytyn hoitovuoden budjetti
          budjettitavoite-vuodelle (some #(when (= (:hoitokauden-alkuvuosi %) hoitokauden-alkuvuosi) %) budjettitavoite)

          ;; Varmista, että päätöstä ei ole vielä tehty
          olemassaoleva-paatos (first (paatos-kyselyt/hae-hoidonjohtopalkkiopaatokset db {:urakkaid urakkaid
                                                                                          :hoitokauden_alkuvuosi hoitokauden-alkuvuosi}))
          validaatio (if olemassaoleva-paatos
                       (conj validaatio (str "Päätös on jo tehty tälle hoitokaudelle. Päivitä selain."))
                       validaatio)

          ;; Verrataan tietokannan tavoitehintaa saatuun tavoitehintaan
          hoitokauden-lopun-indeksikorjaamaton-tavoitehinta (maarita-hv-lopun-indeksikorjaamaton-tavoitehinta
                                                              db kayttaja hoitokauden-alkuvuosi valittu-hoitokausi urakkaid urakan-alkuvuosi budjettitavoite-vuodelle)
          validaatio (if-not (= (konversio/konvertoi->int hoitokauden-lopun-indeksikorjaamaton-tavoitehinta) (konversio/konvertoi->int (:hv_lopun_indkorjaamaton_tavoitehinta paatos)))
                       (conj validaatio (str "Tavoitehinta ei täsmää suunnitelman kanssa. Suunniteltu tavoitehinta:" hoitokauden-lopun-indeksikorjaamaton-tavoitehinta "€.
                       Päätöksen mukainen tavoitehinta: " (:hv_lopun_indkorjaamaton_tavoitehinta paatos) " €"))
                       validaatio)
          tarjouksen-tavoitehinta (lupaus-palvelu/maarita-urakan-tavoitehinta db urakkaid (pvm/hoitokauden-alkupvm hoitokauden-alkuvuosi))
          validaatio (if-not (= (konversio/konvertoi->int tarjouksen-tavoitehinta) (konversio/konvertoi->int (:tarjouksen_tavoitehinta paatos)))
                       (conj validaatio (str "Tarjouksen tavoitehinta ei täsmää suunnitelman kanssa.
                       Tarjouksen tavoitehinta:" tarjouksen-tavoitehinta "€. Päätöksen mukainen tarjouksen tavoitehinta: " (:tarjouksen-tavoitehinta paatos) " €"))
                       validaatio)

          hoidonjohtopalkkio (:budjetoitu_summa_indeksikorjattu (first (paatos-kyselyt/hae-budjetoitu-hoidonjohtopalkkio-hoitokaudelle db {:urakkaid urakkaid
                                                                                                                                           :alkupvm (pvm/hoitokauden-alkupvm hoitokauden-alkuvuosi)
                                                                                                                                           :loppupvm (pvm/hoitokauden-loppupvm (inc hoitokauden-alkuvuosi))})))
          validaatio (if-not (= (konversio/konvertoi->int hoidonjohtopalkkio) (konversio/konvertoi->int (:hoidonjohtopalkkio paatos)))
                       (conj validaatio (str "Hoidonjohtopalkkio ei täsmää suunnitelman kanssa.
                       Suunniteltu hoidonjohtopalkkio:" hoidonjohtopalkkio "€. Päätöksen mukainen hoidonjohtopalkkio: " (:hoidonjohtopalkkio paatos) " €"))
                       validaatio)
          ;; Luodaan päätöksen mukainen kulu, jos hoitovuoden lopun tavoitehinta poikkeaa yli 5% tarjouksen tavoitehinnasta.
          kulu_id (when (and (not (seq validaatio)) (or (< (:muutosprosentti paatos) -5) (> (:muutosprosentti paatos) 5)))
                    (paatos-apurit/tallenna-kulu db paatos kayttaja :hoidonjohtopalkkion-muutos
                      ;; Osassa päätöksistä summat on ristiriidassa kulujen summien kanssa. Joten ne muokataan aina kululle eri päin.
                      ;; Tehdään se hoidonjohtopalkiolle tässä, niin se menee kululle oikein
                      (* -1 (:hoidonjohtopalkkio_muutos paatos)) false))

          ;; Jos muutosprosentti on yli 5%, niin kulu luodaan, muuten kulua ei luoda. Kulu voi olla siis nil
          paatos (assoc paatos :kulu_id kulu_id)

          _ (if (seq validaatio)
              (heita-virhe (str "Virheellinen päätös: " (string/join ", " validaatio)))
              (paatos-kyselyt/tee-hoidonjohtopalkkiomuutospaatos db paatos))]
      ;; Hae välikatselmuksen tiedot
      (hae-valikatselmuksen-tiedot-hoitovuodelle db kayttaja {:urakkaid (:urakkaid paatos) :hoitovuosi (:hoitokauden_alkuvuosi paatos)}))))

(defn poista-hoidonjohtopalkkion-muutospaatos [db kayttaja paatos]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-kulut-valikatselmus kayttaja (:urakkaid paatos))
  (log/debug "poista-hoidonjohtopalkkion-muutospaatos :: paatos" (pr-str paatos))
  (jdbc/with-db-transaction [db db]
    (let [;; Jos päätöksellä on kulu, niin poisteatan se samalla
          _ (when (:kulu_id paatos)
              (kulut-palvelu/poista-kulu-tietokannasta db kayttaja
                {:urakka-id (:urakkaid paatos) :id (:kulu_id paatos)}))
          _ (paatos-kyselyt/poista-hoidonjohtopalkkiomuutospaatos db (:urakkaid paatos) (:id kayttaja) (:id paatos))]

      ;; Hae välikatselmuksen tiedot
      (hae-valikatselmuksen-tiedot-hoitovuodelle db kayttaja {:urakkaid (:urakkaid paatos) :hoitovuosi (:hoitokauden_alkuvuosi paatos)}))))

(defn tee-poytakirjan-raporttipaatos [db kayttaja paatos]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-kulut-valikatselmus kayttaja (:urakkaid paatos))
  (log/info "tee-poytakirjan-raporttipaatos :: paatos" (pr-str paatos))
  (jdbc/with-db-transaction [db db]

    ;; Varmista, että päätöstä ei ole vielä tehty
    (let [validaatio #{}
          olemassaoleva-paatos (first (paatos-kyselyt/hae-poytakirjan-raporttipaatokset db {:urakkaid (:urakkaid paatos)
                                                                                            :hoitokauden_alkuvuosi (:hoitokauden_alkuvuosi paatos)}))
          validaatio (if olemassaoleva-paatos
                       (conj validaatio (str "Päätös on jo tehty tälle hoitokaudelle. Päivitä selain."))
                       validaatio)

          _ (if (seq validaatio)
              (heita-virhe (str "Virheellinen päätös: " (string/join ", " validaatio)))
              (paatos-kyselyt/tee-poytakirjan-raporttipaatos db paatos))]
      ;; Hae välikatselmuksen tiedot
      (hae-valikatselmuksen-tiedot-hoitovuodelle db kayttaja {:urakkaid (:urakkaid paatos) :hoitovuosi (:hoitokauden_alkuvuosi paatos)}))))

(defn poista-poytakirjan-raporttipaatos [db kayttaja paatos]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-kulut-valikatselmus kayttaja (:urakkaid paatos))
  (log/debug "poista-poytakirjan-raporttipaatos :: paatos" (pr-str paatos))
  (jdbc/with-db-transaction [db db]
    (let [_ (paatos-kyselyt/poista-poytakirjan-raporttipaatos db (:urakkaid paatos) (:id kayttaja) (:id paatos))]

      ;; Hae välikatselmuksen tiedot
      (hae-valikatselmuksen-tiedot-hoitovuodelle db kayttaja {:urakkaid (:urakkaid paatos) :hoitovuosi (:hoitokauden_alkuvuosi paatos)}))))

(defn hae-urakan-hintoihin-vaikuttavat-tehdyt-paatokset [db urakkaid mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi
                                                         kuluva-hoitovuosi toteutuneet-kustannukset
                                                         hoitovuoden-lopun-kattohinta hoitovuoden-lopun-tavoitehinta]
  (let [; Haetaan ensin kaikki mahdolliset päätökset
        mahdolliset-paatokset (paatoskone/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi kuluva-hoitovuosi)
        mahdolliset-paatokset (paatoskone/filtteroi-mahdolliset-paatokset mahdolliset-paatokset toteutuneet-kustannukset hoitovuoden-lopun-kattohinta hoitovuoden-lopun-tavoitehinta)
        ;; Poistetaan mahdollinen raporttipäätös
        mahdolliset-paatokset (remove (fn [rivi] (= (:nimi rivi) "Välikatselmuspöytäkirjaan liitettävät raportit")) mahdolliset-paatokset)
        ;; Haetaan tietokantaan mahdollisesti tallennetut päätökset
        tietokanta-paatokset (paatos-kyselyt/hae-paatokset db mahdolliset-paatokset urakkaid kuluva-hoitovuosi)
        ;; Poistetaan mahdollinen raporttipäätös
        tietokanta-paatokset (remove (fn [rivi] (= (:nimi rivi) "Välikatselmuspöytäkirjaan liitettävät raportit")) tietokanta-paatokset)]
    tietokanta-paatokset))

(defn hae-urakan-mahdolliset-paatokset
  "Yhteinen päätöstenhakufunktio"
  [db kayttaja {:keys [urakkaid kuluva-hoitovuosi]}]
  (let [urakan-tiedot (first (q-urakat/hae-urakan-tiedot db urakkaid))
        urakan-alkuvuosi (-> urakan-tiedot :alkupvm pvm/vuosi)
        urakan-loppuvuosi (dec (-> urakan-tiedot :loppupvm pvm/vuosi)) ;; Viimeisen hoitovuoden alkuvuosi käytännössä
        hoitokauden-alkupvm (pvm/hoitokauden-alkupvm kuluva-hoitovuosi)
        hoitokauden-loppupvm (pvm/hoitokauden-loppupvm (inc kuluva-hoitovuosi))
        valittu-hoitokausi [hoitokauden-alkupvm hoitokauden-loppupvm]
        mhu+urakka? (= "mhu+" (:sopimustyyppi urakan-tiedot))
        mhu-tyyppi (paatoskone/urakan-hoitotyyppi mhu+urakka?)
        ;; Haetaan urakan taloustiedot, jotta tiedetään kuuluuko tavoitehinnan alitus, ylitys ja kattohinta pakettiin
        kustannukset (hae-kustannukset-jarjestettyna db urakkaid kuluva-hoitovuosi
                       (pvm/hoitokauden-alkupvm kuluva-hoitovuosi) (pvm/hoitokauden-loppupvm (inc kuluva-hoitovuosi)))
        toteutuneet-kustannukset (get-in kustannukset [:yhteensa :yht-toteutunut-summa])
        budjettitavoite (budjettisuunnittelu-q/hae-budjettitavoite db {:urakka urakkaid})
        ;; Otetaan käytyn hoitovuoden budjetti
        budjettitavoite-vuodelle (some #(when (= (:hoitokauden-alkuvuosi %) kuluva-hoitovuosi) %) budjettitavoite)
        hoitovuoden-lopun-kattohinta (:kattohinta-oikaistu budjettitavoite-vuodelle)
        hoitovuoden-lopun-tavoitehinta (maarita-hv-lopun-indeksikorjattu-tavoitehinta
                                         db kayttaja
                                         kuluva-hoitovuosi
                                         valittu-hoitokausi
                                         urakkaid
                                         urakan-alkuvuosi
                                         budjettitavoite-vuodelle)

        ; Haetaan ensin kaikki mahdolliset päätökset
        mahdolliset-paatokset (paatoskone/kaikki-mahdolliset-paatokset
                                mhu-tyyppi
                                urakan-alkuvuosi
                                urakan-loppuvuosi
                                kuluva-hoitovuosi)
        mahdolliset-paatokset (paatoskone/filtteroi-mahdolliset-paatokset
                                mahdolliset-paatokset
                                toteutuneet-kustannukset
                                hoitovuoden-lopun-kattohinta
                                hoitovuoden-lopun-tavoitehinta)]
    mahdolliset-paatokset))

(defn palauta-kaikki-mahdolliset-ja-tehdyt-paatokset-kojelautaan
  "Palauttaa urakalle mahdolliset, sekä kaikki tehdyt päätökset. Ei poissulje mitään."
  [db kayttaja {:keys [urakkaid kuluva-hoitovuosi] :as tiedot}]
  ;; Huomaa, että tämä on oikeutettu urakkatilanne näkymään
  (oikeudet/vaadi-lukuoikeus oikeudet/urakkatilanne kayttaja)
  (let [mahdolliset-paatokset (hae-urakan-mahdolliset-paatokset db kayttaja tiedot)
        ;; Haetaan tietokantaan mahdollisesti tallennetut päätökset
        tietokanta-paatokset (paatos-kyselyt/hae-paatokset db mahdolliset-paatokset urakkaid kuluva-hoitovuosi)
        ;; Muodostetaan vastaus
        vastaus {:mahdolliset-paatokset mahdolliset-paatokset
                 :tietokanta-paatokset tietokanta-paatokset}]
    vastaus))

(defn onko-paatoksia-tekematta
  "Päätellään onko jokin päätös vielä tekemättä, mikä voi vaikuttaa lukituksiin. 
  HOX jotkin päätökset lasketaan tästä pois."
  [db kayttaja {:keys [urakkaid kuluva-hoitovuosi] :as tiedot}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-lupaukset kayttaja urakkaid)
  (let [mahdolliset-paatokset (hae-urakan-mahdolliset-paatokset db kayttaja tiedot)

        ;; Poistetaan mahdollinen raporttipäätös
        mahdolliset-paatokset (remove (fn [rivi] (= (:nimi rivi) "Välikatselmuspöytäkirjaan liitettävät raportit")) mahdolliset-paatokset)
        ;; Haetaan tietokantaan mahdollisesti tallennetut päätökset
        tietokanta-paatokset (paatos-kyselyt/hae-paatokset db mahdolliset-paatokset urakkaid kuluva-hoitovuosi)
        ;; Poistetaan mahdollinen raporttipäätös
        tietokanta-paatokset (remove (fn [rivi] (= (:nimi rivi) "Välikatselmuspöytäkirjaan liitettävät raportit")) tietokanta-paatokset)

        ;; Muodostetaan vastaus
        vastaus (not (or (= (count mahdolliset-paatokset) (count tietokanta-paatokset)) false))]
    vastaus))

(defrecord Valikatselmukset []
  component/Lifecycle
  (start [this]
    (let [http (:http-palvelin this)
          db (:db this)]
      (julkaise-palvelu http :tallenna-tavoitehinnan-oikaisu
        (fn [user tiedot]
          (tallenna-tavoitehinnan-oikaisu db user tiedot)))
      (julkaise-palvelu http :poista-tavoitehinnan-oikaisu
        (fn [user tiedot]
          (poista-tavoitehinnan-oikaisu db user tiedot)))
      (julkaise-palvelu http :tallenna-kattohinnan-oikaisu
        (fn [user tiedot]
          (tallenna-kattohinnan-oikaisu db user tiedot)))
      (julkaise-palvelu http :poista-kattohinnan-oikaisu
        (fn [user tiedot]
          (poista-kattohinnan-oikaisu db user tiedot)))
      (julkaise-palvelu (:http-palvelin this)
        :hae-valikatselmuksen-tiedot-hoitovuodelle
        (fn [user tiedot]
          (hae-valikatselmuksen-tiedot-hoitovuodelle (:db this) user tiedot)))
      (julkaise-palvelu (:http-palvelin this)
        :onko-paatoksia-tekematta
        (fn [user tiedot]
          (onko-paatoksia-tekematta (:db this) user tiedot)))
      (julkaise-palvelu (:http-palvelin this)
        :tee-lupauspaatos
        (fn [user tiedot]
          (tee-lupauspaatos (:db this) user tiedot))
        {:kysely-spec ::valikatselmus-domain/lupauspaatos})
      (julkaise-palvelu (:http-palvelin this)
        :poista-lupauspaatos
        (fn [user tiedot]
          (poista-lupauspaatos (:db this) user tiedot))
        {:kysely-spec ::valikatselmus-domain/lupauspaatos})
      (julkaise-palvelu (:http-palvelin this)
        :tee-tavoitehinnan-muutospaatos
        (fn [user tiedot]
          (tee-tavoitehinnan-muutospaatos (:db this) user tiedot))
        {:kysely-spec ::valikatselmus-domain/tavoitehinnan-muutospaatos})
      (julkaise-palvelu (:http-palvelin this)
        :poista-tavoitehinnan-muutospaatos
        (fn [user tiedot]
          (poista-tavoitehinnan-muutospaatos (:db this) user tiedot))
        {:kysely-spec ::valikatselmus-domain/tavoitehinnan-muutospaatos})
      (julkaise-palvelu (:http-palvelin this)
        :tee-tavoitehinnan-alituspaatos
        (fn [user tiedot]
          (tee-tavoitehinnan-alituspaatos (:db this) user tiedot))
        {:kysely-spec ::valikatselmus-domain/tavoitehinnan-alituspaatos})
      (julkaise-palvelu (:http-palvelin this)
        :poista-tavoitehinnan-alituspaatos
        (fn [user tiedot]
          (poista-tavoitehinnan-alituspaatos (:db this) user tiedot))
        {:kysely-spec ::valikatselmus-domain/tavoitehinnan-alituspaatos})
      (julkaise-palvelu (:http-palvelin this)
        :tee-tavoitehinnan-ylityspaatos
        (fn [user tiedot]
          (tee-tavoitehinnan-ylityspaatos (:db this) user tiedot))
        {:kysely-spec ::valikatselmus-domain/tavoitehinnan-ylityspaatos})
      (julkaise-palvelu (:http-palvelin this)
        :poista-tavoitehinnan-ylityspaatos
        (fn [user tiedot]
          (poista-tavoitehinnan-ylityspaatos (:db this) user tiedot))
        {:kysely-spec ::valikatselmus-domain/tavoitehinnan-ylityspaatos})
      (julkaise-palvelu (:http-palvelin this)
        :tee-kattohinnan-ylityspaatos
        (fn [user tiedot]
          (tee-kattohinnan-ylityspaatos (:db this) user tiedot))
        {:kysely-spec ::valikatselmus-domain/kattohinnan-ylityspaatos})
      (julkaise-palvelu (:http-palvelin this)
        :poista-kattohinnan-ylityspaatos
        (fn [user tiedot]
          (poista-kattohinnan-ylityspaatos (:db this) user tiedot))
        {:kysely-spec ::valikatselmus-domain/kattohinnan-ylityspaatos})
      (julkaise-palvelu (:http-palvelin this)
        :tee-indeksikorjauspaatos
        (fn [user tiedot]
          (tee-indeksikorjauspaatos (:db this) user tiedot))
        {:kysely-spec ::valikatselmus-domain/indeksikorjauspaatos})
      (julkaise-palvelu (:http-palvelin this)
        :poista-indeksikorjauspaatos
        (fn [user tiedot]
          (poista-indeksikorjauspaatos (:db this) user tiedot))
        {:kysely-spec ::valikatselmus-domain/indeksikorjauspaatos})
      (julkaise-palvelu (:http-palvelin this)
        :tee-hv-lopun-tavoite-ja-kattohintapaatos
        (fn [user tiedot]
          (tee-hv-lopun-tavoite-ja-kattohintapaatos (:db this) user tiedot))
        {:kysely-spec ::valikatselmus-domain/hoitokauden-lopun-hintapaatos})
      (julkaise-palvelu (:http-palvelin this)
        :poista-hoitovuoden-lopun-hintapaatos
        (fn [user tiedot]
          (poista-hoitovuoden-lopun-hintapaatos (:db this) user tiedot))
        {:kysely-spec ::valikatselmus-domain/hoitokauden-lopun-hintapaatos})
      (julkaise-palvelu (:http-palvelin this)
        :tee-hoidonjohtopalkkion-muutospaatos
        (fn [user tiedot]
          (tee-hoidonjohtopalkkion-muutospaatos (:db this) user tiedot))
        {:kysely-spec ::valikatselmus-domain/hoidonjohtopalkkiomuutospaatos})
      (julkaise-palvelu (:http-palvelin this)
        :poista-hoidonjohtopalkkion-muutospaatos
        (fn [user tiedot]
          (poista-hoidonjohtopalkkion-muutospaatos (:db this) user tiedot))
        {:kysely-spec ::valikatselmus-domain/hoidonjohtopalkkiomuutospaatos})
      (julkaise-palvelu (:http-palvelin this)
        :tee-poytakirjan-raporttipaatos
        (fn [user tiedot]
          (tee-poytakirjan-raporttipaatos (:db this) user tiedot))
        {:kysely-spec ::valikatselmus-domain/raporttipaatos})
      (julkaise-palvelu (:http-palvelin this)
        :poista-poytakirjan-raporttipaatos
        (fn [user tiedot]
          (poista-poytakirjan-raporttipaatos (:db this) user tiedot))
        {:kysely-spec ::valikatselmus-domain/raporttipaatos})
      this))
  (stop [this]
    (poista-palvelut (:http-palvelin this)
      :tallenna-tavoitehinnan-oikaisu
      :poista-tavoitehinnan-oikaisu
      :tallenna-kattohinnan-oikaisu
      :poista-kattohinnan-oikaisu
      :hae-valikatselmuksen-tiedot-hoitovuodelle
      :onko-paatoksia-tekematta
      :tee-lupauspaatos
      :poista-lupauspaatos
      :tee-tavoitehinnan-muutospaatos
      :poista-tavoitehinnan-muutospaatos
      :poista-tavoitehinnan-pysyvamuutospaatos
      :tee-tavoitehinnan-alituspaatos
      :poista-tavoitehinnan-alituspaatos
      :tee-tavoitehinnan-ylityspaatos
      :poista-tavoitehinnan-ylityspaatos
      :tee-kattohinnan-ylityspaatos
      :poista-kattohinnan-ylityspaatos
      :tee-indeksikorjauspaatos
      :poista-indeksikorjauspaatos
      :tee-hv-lopun-tavoite-ja-kattohintapaatos
      :poista-hoitovuoden-lopun-hintapaatos
      :tee-hoidonjohtopalkkion-muutospaatos
      :poista-hoidonjohtopalkkion-muutospaatos
      :tee-poytakirjan-raporttipaatos
      :poista-poytakirjan-raporttipaatos)
    this))
