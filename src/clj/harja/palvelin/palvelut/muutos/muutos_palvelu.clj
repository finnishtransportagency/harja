(ns harja.palvelin.palvelut.muutos.muutos-palvelu
  (:require [clojure.set :as set]
            [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [com.stuartsierra.component :as component]
            [slingshot.slingshot :refer [throw+]]

            [harja.pvm :as pvm]
            [harja.fmt :as fmt]

            [harja.domain.mhu :as mhu]
            [harja.domain.oikeudet :as oikeudet]
            [harja.domain.kulut :as kulut-domain]
            [harja.domain.muutos-domain :as muutos-domain]

            [harja.kyselyt.konversio :as konv]
            [harja.kyselyt.urakat :as q-urakat]
            [harja.kyselyt.kulut :as kulu-kyselyt]
            [harja.kyselyt.liitteet :as liite-kyselyt]
            [harja.kyselyt.indeksit :as indeksi-kyselyt]
            [harja.kyselyt.toimenpideinstanssit :as tpi-q]
            [harja.kyselyt.budjettisuunnittelu :as budjettisuunnittelu-q]
            [harja.kyselyt.uusi-kustannussuunnitelma-kyselyt :as ks-kyselyt]
            [harja.kyselyt.muutos-kyselyt :as muutos-kyselyt]
            [harja.kyselyt.rahavaraukset :as rahavaraus-kyselyt]
            [harja.kyselyt.tehtavamaarat :as tehtavamaarat-kyselyt]

            [harja.palvelin.palvelut.muutos.muutos-apurit :as muutos-apurit]
            [harja.palvelin.asetukset :refer [ominaisuus-kaytossa?]]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]
            [harja.palvelin.tyokalut.tyokalut :as tyokalut]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelut poista-palvelut]]))

(defn paivita-muutostyo-kulu-kohdistus
  "Kululomake kutsuu, ei liity automaattisiin kuluihin"
  [db muutostyo kohdistus-id]
  (when (and
          kohdistus-id (:id muutostyo))
    (muutos-kyselyt/paivita-muutostyo-kulukohdistus! db {:muutos (:id muutostyo)
                                                         :kohdistus-id kohdistus-id})))

(defn hae-hoitovuosien-yksikkohinnat
  [db _kayttaja {:keys [urakka-id hoitokaudet tehtava_id nakyma-valittu-hk] :as _tiedot}]
  (map-indexed (fn [idx valittu-hoitokausi]
                 (let [hoitokauden-alkuvuosi (pvm/vuosi (first valittu-hoitokausi))
                       ;; Tarkista, onhan yksikköhinta edelliseltä vuodelta (siihen mikä on valittuna)
                       yksikkohinta-aiemmalta-vuodelta? (and
                                                          nakyma-valittu-hk
                                                          valittu-hoitokausi
                                                          (> nakyma-valittu-hk (pvm/vuosi (first valittu-hoitokausi))))]

                   (when yksikkohinta-aiemmalta-vuodelta?
                     (let [alkupvm (str hoitokauden-alkuvuosi "-10-01")
                           loppupvm (str (inc hoitokauden-alkuvuosi) "-09-30")
                           params {:urakka urakka-id
                                   :tehtavaryhma nil
                                   :alkupvm alkupvm
                                   :loppupvm loppupvm
                                   :tehtava (or tehtava_id nil)
                                   :hoitokauden_alkuvuosi hoitokauden-alkuvuosi
                                   :laskenta-automatiikka? true
                                   :talvisuolakerroin muutos-domain/+talvisuolakerroin+}
                           yksikkohinta (->
                                          (muutos-kyselyt/hae-tehtava-maaramuutokset db params)
                                          first :yksikkohinta)]
                       ;; esim:: 
                       ;; 7,90 (1. hoitovuoden yksikköhinta)
                       {:valinta (str
                                   yksikkohinta " "
                                   "(" (inc idx) ". hoitovuoden yksikköhinta)")
                        :arvo yksikkohinta
                        :hk-nro (inc idx)
                        :hoitokauden-alkuvuosi hoitokauden-alkuvuosi}))))
    hoitokaudet))


(defn hae-tehtava-maaramuutokset
  [db
   {:keys [id] :as kayttaja}
   {:keys [urakka-id valittu-hoitokausi hoitokaudet laskenta-automatiikka?] :as _tiedot}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu kayttaja urakka-id)
  (let [hoitokauden-alkuvuosi (pvm/vuosi (first valittu-hoitokausi))
        alkupvm (str hoitokauden-alkuvuosi "-10-01")
        loppupvm (str (inc hoitokauden-alkuvuosi) "-09-30")
        params {:tehtava nil
                :tehtavaryhma nil
                :urakka urakka-id
                :alkupvm alkupvm
                :loppupvm loppupvm
                :hoitokauden_alkuvuosi hoitokauden-alkuvuosi
                :laskenta-automatiikka? laskenta-automatiikka?
                :talvisuolakerroin muutos-domain/+talvisuolakerroin+}
        vastaus (muutos-kyselyt/hae-tehtava-maaramuutokset db params)

        fn-lisaa-valiotsikot (fn [rivit]
                               ;; Ottaa gridin rivit, joista jokainen sisältää :toimenpide arvon 
                               ;; Jokaista uutta :toimenpide -arvoa kohden lisätään {:valiotsikko <toimenpide>} 
                               (let [step (fn [[nahty acc] rivi]
                                            (let [tp (:toimenpide rivi)]
                                              (if (contains? nahty tp)
                                                ;; kyseinen toimenpide on jo nähty 
                                                [nahty (conj acc rivi)]
                                                ;; toimenpide ilmestyy ensimmäistä kertaa, lisää väliotsikko
                                                [(conj nahty tp)
                                                 (conj acc {:valiotsikko tp :id (gensym)} rivi)])))]

                                 (->> rivit
                                   (reduce step [#{} []]) second)))

        tarkistetut-rivit (map (fn [{:keys [syy
                                            maara
                                            tehtava_id
                                            talvisuola
                                            yksikkohinnan_lahde
                                            talvisuola_kerroin
                                            suunniteltu_maara
                                            kirjatut_kulut_summa
                                            yksikkohinnan_alkuvuosi
                                            syotetty_tavoitehintamuutos] :as rivi}]

                                 (let [tehtavalla-ei-toteumia? (or
                                                                 (not maara)
                                                                 (<= maara 0))

                                       ;; Yksikköhinta = kulut / toteumat 
                                       laskettu-yksikkohinta (when
                                                               (and
                                                                 (> (bigdec maara) 0M)
                                                                 (> (bigdec kirjatut_kulut_summa) 0M))
                                                               (with-precision 4
                                                                 (/ (bigdec kirjatut_kulut_summa) (bigdec maara))))

                                       laskettu-maaramuutos (- maara suunniteltu_maara)

                                       ;; Jos  yksikköhinta on asetettu, mutta tehtävälle tuleekin toteumia
                                       ;; tilanne täytyy pävittää kantaan jotta harja tietää mitä harjailee 
                                       paivita-laskenta-uusi-urakka? (and
                                                                       laskenta-automatiikka?
                                                                       (> maara 0)
                                                                       (> kirjatut_kulut_summa 0)
                                                                       ;; Tavhinta muutos on syötetty käsin, mutta toteumia tullut 
                                                                       syotetty_tavoitehintamuutos)

                                       paivita-laskenta-vanha-urakka? (and
                                                                        (> maara 0) ;; tälle vuodelle olemassa toteumia 
                                                                        yksikkohinnan_alkuvuosi) ;; mutta yksikköhinta asetettu? => päivitä kanta 
                                       rivi (if (or
                                                  paivita-laskenta-vanha-urakka?
                                                  paivita-laskenta-uusi-urakka?)
                                              (do
                                                (muutos-kyselyt/paivita-tehtava-tiedot<! db {:syy syy
                                                                                             :lahde "laskettu"
                                                                                             :kayttaja id
                                                                                             :urakka urakka-id
                                                                                             :tehtava tehtava_id
                                                                                             :hk_alkuvousi hoitokauden-alkuvuosi
                                                                                             :yksikkohinta_hk_alkuvuosi nil
                                                                                             :kasin_syotetty_tavoitehinta nil})
                                                (assoc rivi
                                                  :lahde "laskettu"
                                                  :yksikkohinta_hk_alkuvuosi nil
                                                  :kasin_syotetty_tavoitehinta nil
                                                  :yksikkohinta laskettu-yksikkohinta
                                                  ;; Tavoitehinnan muutos = Määrämuutos * yksikköhinta  
                                                  :tavoitehinnan_muutos (*
                                                                          laskettu-maaramuutos
                                                                          laskettu-yksikkohinta)))
                                              rivi)


                                       ;; Jos toteumia ei ole, ei voida yksikköhintaa laskea
                                       ;; => Yritä hakea yksikköhinta edellisiltä vuosilta 
                                       kaikki-yksikkohinnat (when (and
                                                                    laskenta-automatiikka?
                                                                    tehtavalla-ei-toteumia?)
                                                              (hae-hoitovuosien-yksikkohinnat db kayttaja {:urakka-id urakka-id
                                                                                                           :hoitokaudet hoitokaudet
                                                                                                           :tehtava_id (:tehtava_id rivi)
                                                                                                           :nakyma-valittu-hk hoitokauden-alkuvuosi}))
                                       ;; Jos edellisiltä vuosilta löytyi yksikköhinta, tarjotaan niitä gridiin 
                                       aikaisemmat-yksikkohinnat (filter #(and
                                                                            ;; Vaadi että jokin yksikköhinta saatavilla 
                                                                            (some? (:arvo %))
                                                                            ;; Suodata kuluva hk pois, tulee mukaan esim jos yksikköhinnan lähde on valittu  
                                                                            (not= hoitokauden-alkuvuosi (:hoitokauden-alkuvuosi %))) kaikki-yksikkohinnat)

                                       loytyi-aikaisemmat-yksikkohinnat? (some? (seq aikaisemmat-yksikkohinnat))

                                       ;; Jos ei edellisiä yksikköhintojakaan löytynyt, anna kirjata tavoitehinta manuaalisesti 
                                       anna-kirjata-tavoitehinta? (and
                                                                    (or (not laskenta-automatiikka?) tehtavalla-ei-toteumia?)
                                                                    (not loytyi-aikaisemmat-yksikkohinnat?))

                                       ;; Hae nykyhetken asetettu hoitokauden yksikköhinta 
                                       asetettu-yksikkohinta (filter #(= (:hoitokauden-alkuvuosi %) yksikkohinnan_alkuvuosi)
                                                               kaikki-yksikkohinnat)

                                       yksikkohinta (or
                                                      (-> asetettu-yksikkohinta first :arvo)
                                                      (:yksikkohinta rivi)
                                                      0.0)

                                       ;; Määrämuutos  =  Toteutunut määrä - suunniteltu määrä 
                                       ;; Tavoitehinnan muutos = Määrämuutos * yksikköhinta  
                                       tavoitehinnan_muutos (or (:tavoitehinnan_muutos rivi)
                                                              (* (- maara suunniteltu_maara) yksikkohinta))

                                       ;; Vanhemmat urakat 
                                       kayta-talviuola-kerrointa? (and
                                                                    ;; Kyseessä talvisuola 
                                                                    talvisuola
                                                                    ;; Ei ole käsin syötettyä arvoa
                                                                    (not (:tavoitehinnan_muutos rivi))
                                                                    ;; Toteutunut on alle suunnitellun 
                                                                    (> maara 0M)
                                                                    (> suunniteltu_maara maara))

                                       ;; Uudemmat urakat (autom. laskenta) 
                                       kayta-talviuola-kerrointa? (or kayta-talviuola-kerrointa?
                                                                    (and
                                                                      talvisuola
                                                                      laskenta-automatiikka?
                                                                      (or syotetty_tavoitehintamuutos (not= yksikkohinnan_lahde "puuttuu")) ;; Käsin syötetty, mutta toteumia tullut
                                                                      ;; Toteutunut on alle suunnitellun 
                                                                      (> maara 0M)
                                                                      (> suunniteltu_maara maara)))

                                       ;; Lisää talvisuolalle talvisuoran kerroin 
                                       ;; Kerroin lisätään, jos toteutunut on alle suunnitellun
                                       ;; Kerroin on definattu sql kyselyssä name: hae-tehtava-maaramuutokset
                                       tavoitehinnan_muutos (if kayta-talviuola-kerrointa?
                                                              (* tavoitehinnan_muutos talvisuola_kerroin)
                                                              tavoitehinnan_muutos)]

                                   (assoc rivi
                                     :yksikkohinta yksikkohinta
                                     :tavoitehinnan_muutos tavoitehinnan_muutos
                                     :aikaisemmat-yksikkohinnat aikaisemmat-yksikkohinnat
                                     :anna-kirjata-tavoitehinta? anna-kirjata-tavoitehinta?))) vastaus)

        ;; Lisätään taulukkoon vielä design mukaiset väliotsikot
        tarkistetut-rivit (fn-lisaa-valiotsikot tarkistetut-rivit)]
    tarkistetut-rivit))


(defn tallenna-tehtava-maaramuutokset
  [db
   {:keys [id] :as kayttaja}
   {:keys [urakka-id valittu-hoitokausi hoitokaudet rivit] :as _tiedot}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu kayttaja urakka-id)
  ;; Kutsutaan gridin tallenna painikkeesta 
  (let [hoitokauden-alkuvuosi (pvm/vuosi (first valittu-hoitokausi))]
    (jdbc/with-db-transaction [conn db]
      (doseq [rivi rivit]
        (let [{:keys [syy
                      tehtava_id
                      _yksikkohinta
                      valitun_yksikkohinnan_hoitokausi
                      tavoitehinnan_muutos
                      anna-kirjata-tavoitehinta?]} rivi

              lahde (cond
                      ;; Tavoitehinta on kirjattu käsin 
                      ;; => yksikköhinta puuttuu
                      (and
                        tavoitehinnan_muutos
                        anna-kirjata-tavoitehinta?
                        (not= tavoitehinnan_muutos 0))
                      "puuttuu"

                      ;; Yksikköhinta on valittu edelliseltä vuodelta 
                      ;; => valittu 
                      (and
                        valitun_yksikkohinnan_hoitokausi
                        (> valitun_yksikkohinnan_hoitokausi 0))
                      "valittu"

                      ;; Muulloin yksikköhinta on laskettu automaattisesti (kaikki data on saatavilla)
                      :else "laskettu")

              params {:syy syy
                      :lahde lahde
                      :kayttaja id
                      :urakka urakka-id
                      :tehtava tehtava_id
                      :hk_alkuvousi hoitokauden-alkuvuosi
                      :yksikkohinta_hk_alkuvuosi valitun_yksikkohinnan_hoitokausi
                      :kasin_syotetty_tavoitehinta (when (= lahde "puuttuu") tavoitehinnan_muutos)}]
          (muutos-kyselyt/paivita-tehtava-tiedot<! conn params))))

    (hae-tehtava-maaramuutokset db kayttaja {:urakka-id urakka-id
                                             :hoitokaudet hoitokaudet
                                             :valittu-hoitokausi valittu-hoitokausi})))


(defn tallenna-maaramuutos-yksikkohinta [db
                                         {:keys [id] :as kayttaja}
                                         {:keys [urakka-id valittu-hoitokausi rivi hoitokaudet] :as _tiedot}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu kayttaja urakka-id)
  ;; Valitun rivin tiedot 
  (let [{:keys [syy
                tehtava_id
                yksikkohinnan_alkuvuosi]} rivi
        ;; Tätä kutsutaan modalista, joten lähde on aina valittu
        ;; (yksikköhinta on asetettu edellisiltä hoitokausilta)>
        poista-yksikkohinta? (nil? yksikkohinnan_alkuvuosi) ;; Frontissa on valinta "Ei yksikköhintaa"
        lahde (if poista-yksikkohinta? "puuttuu" "valittu")
        hoitokauden-alkuvuosi (pvm/vuosi (first valittu-hoitokausi))
        params {:syy syy
                :lahde lahde
                :kayttaja id
                :urakka urakka-id
                :tehtava tehtava_id
                :kasin_syotetty_tavoitehinta (if poista-yksikkohinta? 0 nil)
                :hk_alkuvousi hoitokauden-alkuvuosi
                :yksikkohinta_hk_alkuvuosi yksikkohinnan_alkuvuosi}
        _ (muutos-kyselyt/paivita-tehtava-tiedot<! db params)]

    (hae-tehtava-maaramuutokset db kayttaja (assoc params
                                              :urakka-id urakka-id
                                              :hoitokaudet hoitokaudet
                                              :valittu-hoitokausi valittu-hoitokausi))))

(defn urakan-tavoitehinnat-indeksikorjattu
  "Palauttaa hoitokausien alkuvuodet, joille urakan tavoitehinnat on indeksikorjattu ja vahvistettu"
  [db urakka-id]
  (assert (int? urakka-id) "Urakka-id tulee olla kokonaisluku")

  (let [urakan-hoitokaudet (q-urakat/hae-urakan-hoitokaudet db urakka-id)
        tavoitehintojen-tilat (budjettisuunnittelu-q/urakan-tavoitehintojen-tilat db urakka-id)]
    (into {}
      (mapv (fn [hoitokausi]
              (let [hoitokauden-alkuvuosi (pvm/vuosi (:alkupvm hoitokausi))
                    vahvistettu? (boolean (some #(and (= (:hoitokauden-alkuvuosi %) hoitokauden-alkuvuosi)
                                                   (:indeksikorjaus-vahvistettu %))
                                            tavoitehintojen-tilat))]
                [hoitokauden-alkuvuosi vahvistettu?]))
        urakan-hoitokaudet))))


(defn- laske-indeksikorjattu-summa
  "Indeksikorjattu summa lasketaan summasta ja urakan voimassaolevista indekseistä.
  Jos summaa ei ole annettu tai indeksiä hoitovuodelle ei löydy, palautetaan nil."
  [summa urakan-indeksit hoitovuosi-nro]
  (when summa
    (indeksi-kyselyt/indeksikorjaa
      (indeksi-kyselyt/indeksikerroin urakan-indeksit hoitovuosi-nro) summa)))

(defn indeksikorjaa-tavoitehinnan-muutokset [db urakka-id hoitokauden-alkuvuosi muutokset]
  (if (seq muutokset)
    (let [urakka (first (q-urakat/hae-urakka db urakka-id))
          hoitokauden-nro (pvm/hoitokausivuosi->mhu-hoitovuosi-nro (:alkupvm urakka) hoitokauden-alkuvuosi)
          urakan-indeksit (indeksi-kyselyt/hae-urakan-indeksikertoimet db urakka-id)]
      (map (fn [rivi]
             (assoc rivi :tavoitehinnan-muutos-indeksikorjattu
               (or
                 (laske-indeksikorjattu-summa (:tavoitehinnan-muutos rivi) urakan-indeksit hoitokauden-nro)
                 0)))
        muutokset))
    muutokset))

(defn hae-aiempien-vuosien-pysyvat-muutokset
  ([db urakka-id hoitokauden-alkuvuosi]
   (hae-aiempien-vuosien-pysyvat-muutokset db urakka-id hoitokauden-alkuvuosi true))
  ([db urakka-id hoitokauden-alkuvuosi indeksikorjaa?]
   (let [muutokset (-> (muutos-kyselyt/hae-urakan-hoitovuoden-kirjatut-muutokset db
                         {:urakka urakka-id
                          :hoitokauden_alkuvuosi hoitokauden-alkuvuosi
                          :hae-vain-aiemmat-pysyvat-muutokset? true})
                     (muutos-apurit/parsi-kirjatut-muutokset-vastaus))]

     ;; Lasketaan lopuksi tavoitehintojen muutokset indeksikorjaukset
     (if indeksikorjaa?
       (indeksikorjaa-tavoitehinnan-muutokset db
         urakka-id hoitokauden-alkuvuosi muutokset)
       muutokset))))

(defn hae-urakan-muutostiedot
  [db kayttaja {:keys [urakka-id hoitokaudet valittu-hoitokausi laskenta-automatiikka?] :as _tiedot}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu kayttaja urakka-id)
  (let [hoitokauden-alkuvuosi (pvm/vuosi (first valittu-hoitokausi))
        kirjatut-muutokset (->
                             (muutos-kyselyt/hae-urakan-hoitovuoden-kirjatut-muutokset db
                               {:urakka urakka-id
                                :hoitokauden_alkuvuosi hoitokauden-alkuvuosi
                                :hae-vain-aiemmat-pysyvat-muutokset? false})
                             (muutos-apurit/parsi-kirjatut-muutokset-vastaus))
        aiempien-vuosien-pysyvat-muutokset (hae-aiempien-vuosien-pysyvat-muutokset db urakka-id hoitokauden-alkuvuosi)
        rahavaraukset (rahavaraus-kyselyt/muutosten-rahavaraukset db urakka-id hoitokauden-alkuvuosi)
        budjettitavoiteet (budjettisuunnittelu-q/budjettitavoite-vuodelle db urakka-id hoitokauden-alkuvuosi)
        ;; Mapataan hoitokausien alkuvuodet, joille urakan tavoitehinnat on indeksikorjattu ja vahvistettu
        tavoitehinnat-indeksikorjattu (urakan-tavoitehinnat-indeksikorjattu db urakka-id)

        ;; Tehtävä- ja määrätoteumiin perustuvat tavoitehintamuutokset
        tehtava-ja-maaramuutokset (hae-tehtava-maaramuutokset db kayttaja {:urakka-id urakka-id
                                                                           :hoitokaudet hoitokaudet
                                                                           :valittu-hoitokausi valittu-hoitokausi
                                                                           :laskenta-automatiikka? laskenta-automatiikka?})

        kirjatut-muutokset-yht (reduce + (map :tavoitehinnan-muutos kirjatut-muutokset))
        aiemmat-pysyvat-muutokset-indeksikorjattu-yht (reduce +
                                                        ;; Arvot voivat periaatteessa olla nil, jos indeksikorjausta ei ole
                                                        ;; tehty, joten poistetaan nillit ennen summauksen laskemista
                                                        (remove nil?
                                                          (map :tavoitehinnan-muutos-indeksikorjattu aiempien-vuosien-pysyvat-muutokset)))
        toteumiin-perustuvat-muutokset-yht (reduce + 0
                                             (remove nil?
                                               (concat
                                                 [(:tavoitehinnan-muutos (last rahavaraukset))]
                                                 (map :tavoitehinnan_muutos tehtava-ja-maaramuutokset))))
        laskutusrajan-tarkistukset (ks-kyselyt/hae-laskutusrajan-tarkistukset
                                     db
                                     {:urakka urakka-id
                                      :hoitokauden_alkuvuosi hoitokauden-alkuvuosi
                                      :hoitovuoden_indeksikorjattu_tavoitehinta (:tavoitehinta-indeksikorjattu budjettitavoiteet)})
        muutosten-vaikutus-yht (+
                                 (or (:tavoitehinta-indeksikorjattu budjettitavoiteet) 0)
                                 (or aiemmat-pysyvat-muutokset-indeksikorjattu-yht 0)
                                 kirjatut-muutokset-yht
                                 toteumiin-perustuvat-muutokset-yht)]

    {;; kirjatut muutokset jos hoitokausi 2025-2026 tai jälkeen
     :kirjatut-muutokset kirjatut-muutokset
     :aiempien-hoitovuosien-pysyvat-muutokset aiempien-vuosien-pysyvat-muutokset
     ;; laskennat lasketuille muutoksille jos hoitokausi 2025-2026 tai jälkeen
     :lasketut-muutokset tehtava-ja-maaramuutokset
     :rahavarausten-muutokset rahavaraukset
     :laskutusrajan-tarkistukset laskutusrajan-tarkistukset
     ;; TODO: laskennat vanhojen tavoitehintojen muutoksille jos hoitokausi ennen 2025-2026
     :tavoitehinnan-muutokset []
     ;; TODO: laskennat vanhojen suunniteltujen määrien muutoksille jos hoitokausi ennen 2025-2026
     :suunniteltujen-maarien-muutokset []
     :budjettitavoitteet {:tavoitehinta-indeksikorjattu-per-hoitovuosi tavoitehinnat-indeksikorjattu
                          :hoitovuoden-alun-indeksikorjattu-tavoitehinta (:tavoitehinta-indeksikorjattu budjettitavoiteet)
                          :laskutusraja_kaytossa? (:laskutusraja-kaytossa budjettitavoiteet)
                          :laskutusraja (:laskutusraja budjettitavoiteet)
                          :laskutusraja_alkuperainen (:laskutusraja-alkuperainen budjettitavoiteet)
                          :aiemmat-pysyvat-muutokset-indeksikorjattu-yht aiemmat-pysyvat-muutokset-indeksikorjattu-yht
                          :kirjatut-muutokset-yht kirjatut-muutokset-yht
                          :toteumiin-perustuvat-muutokset-yht toteumiin-perustuvat-muutokset-yht
                          :muutosten-vaikutus-yht muutosten-vaikutus-yht}}))



(defn hae-mhu-suunniteltavat-tehtavat [db urakka-id alkupvm loppupvm]
  (tehtavamaarat-kyselyt/mhu-suunniteltavat-tehtavat db {:urakka urakka-id
                                                         :hoitokausi (range (pvm/vuosi alkupvm)
                                                                       (inc (pvm/vuosi loppupvm)))}))


(defn hae-toimenpiteiden-tehtavat
  "Hakee toimenpiteiden tehtävät. Näitä tarvitaan pysyvissä muutoksissa, mutta voidaan tarvita myös muissa muutostyypeissä."
  [db urakka-id]
  (let [{:keys [alkupvm loppupvm]} (first (q-urakat/hae-urakka db {:id urakka-id}))
        toimenpiteiden-tehtavat (hae-mhu-suunniteltavat-tehtavat db urakka-id alkupvm loppupvm)]

    (->> toimenpiteiden-tehtavat
      (map #(select-keys % #{:jarjestys :tehtava-id :suunniteltu-maara :toimenpidekoodi :tehtava :yksikko :hoitokauden-alkuvuosi})))))

(defn hae-pysyvan-muutoksen-pohjatiedot
  "Hakee pohjatiedot uuden pysyvän muutoksen lomakkeelle"
  [db kayttaja {:keys [urakka-id muutos-id muutos-versio] :as tiedot}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu kayttaja urakka-id)

  (let [toimenpiteiden-tiedot (mapv
                                (fn [rivi]
                                  (-> rivi
                                    (update :budjetoidut_summat #(konv/jsonb->clojuremap %))
                                    (update :kustannusvaikutukset #(konv/jsonb->clojuremap %))
                                    (update :tehtavat_ja_maarat #(konv/jsonb->clojuremap %))))
                                ;; pysyvän muutoksen tietoja voi olla usealla hoitovuodella. Kysely ja palvelu palauttavat kaikkien hoitovuosien tiedot, toimenpiteittäin ryhmiteltynä.
                                ;; Jos muutos-id:tä tai versiota ei ole annettu, haetaan vain pohjatiedot (uusi pysyvä muutos)
                                (muutos-kyselyt/hae-pysyvan-muutoksen-kustannustiedot db {:id muutos-id
                                                                                          :versio muutos-versio
                                                                                          :urakka urakka-id}))
        toimenpiteiden-tehtavat (hae-toimenpiteiden-tehtavat db urakka-id)]
    {:toimenpiteiden-tiedot toimenpiteiden-tiedot
     :toimenpiteiden-tehtavat toimenpiteiden-tehtavat}))

;; TODO: Refaktoroi koodia. Tässä on paljon tyypin perusteella iffittelyä, joka menee helposti hankalalukuiseksi
;;       Mieti uudestaan miten muutostyypin perusteella kannattaa lomakkeen perustietoja hakea
;;       Olemassaolevaa muutosta muokatessa on myös tarpeen hakea muutoksen id:n perusteella lisää tietoja
(defn hae-muutoksen-tiedot
  "Palauttaa yksittäisen muutoksen tarkat tiedot lomaketta varten."
  [db kayttaja {:keys [urakka-id muutos] :as tiedot}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu kayttaja urakka-id)

  (let [tyyppikohtaiset-tiedot (case (:tyyppi muutos)
                                 "johto-ja-hallintokorvaus"
                                 (when (:id muutos)
                                   (mapv
                                     (fn [rivi]
                                       (-> rivi
                                         (update :kulut #(mapv (fn [kulu]
                                                                 (update kulu :pvm pvm/dateksi))
                                                           (konv/jsonb->clojuremap %)))))
                                     (muutos-kyselyt/hae-johto-ja-hallintokorvausmuutoksen-tiedot db {:id (:id muutos)
                                                                                                      :versio (:versio muutos)
                                                                                                      :urakka urakka-id})))

                                 ;; tähän puuttuvien muutostyyppien lomakehaut...
                                 [{}])

        _ (when (> (count tyyppikohtaiset-tiedot) 1)
            (log/error "Muutoksia palautui lomakkeelle enemmän kuin yksi urakassa " urakka-id)
            (throw (Error. "Muutoksia palautui enemmän kuin yksi, kyseessä on luultavasti ongelmatilanne. Ota yhteys Harja-palautteeseen.")))
        liitteet (when-not (empty? (:liite-idt muutos))
                   (liite-kyselyt/hae-liitteiden-tiedot db {:idt (:liite-idt muutos)
                                                            :urakka urakka-id}))
        vastaus (assoc (merge muutos
                         (first tyyppikohtaiset-tiedot)
                         (when (= (:tyyppi muutos) "pysyva")
                           (hae-pysyvan-muutoksen-pohjatiedot db kayttaja {:urakka-id urakka-id
                                                                           :muutos-id (:id muutos)
                                                                           :muutos-versio (:versio muutos)})))
                  :liitteet liitteet)]
    vastaus))


(defn- poista-vanhat-kulutiedot!
  "Asettaa poistetuksi vanhat kulutiedot, jotta ne eivät näy käyttöliittymässä tai raporteissa."
  ;; halutaan saada historiatieto talteen, tämä on siihen käytännöllinen tapa tekemättä valtavaa refaktorointia kulu tauluun (ja sille omaa historiataulua ja triggereitä)
  [db kayttaja rivi]
  ;; FIXME: Tämä on näemmä vielä kesken. Rivin mukana ei tule vielä kulu-id:tä ainakaan testeissä
  (let [kulu-id (:kulu-id rivi)]
    (when kulu-id
      (log/info "Poistetaan vanha kulu id:llä " kulu-id)
      (kulu-kyselyt/poista-kulu! db {:id kulu-id
                                     :kayttaja (:id kayttaja)})
      (kulu-kyselyt/poista-kulun-kohdistukset! db {:id kulu-id
                                                   :kayttaja (:id kayttaja)}))))


(defn- tallenna-johto-ja-hallintokorvauksen-muutokset
  "Tallentaa johto- ja hallintokorvauksen muutokset tietokantaan kuluiksi, linkittää muutokseen."
  [db kayttaja urakka muutos-id-ja-versio rivit]
  (let [hoidon-johto-tpi-id (:id (first
                                   (tpi-q/hae-urakan-toimenpideinstanssi-toimenpidekoodilla db
                                     {:urakka (:id urakka)
                                      :koodi (mhu/toimenpide-avain->toimenpide :mhu-johto)})))]
    (doseq [rivi rivit
            :let [kulu {:urakka (:id urakka)
                        :erapaiva (:pvm rivi)
                        :numero (:numero rivi)
                        :kayttaja (:id kayttaja)
                        :lisatieto "Muutoksesta automaattisesti luotu kulu"
                        :laskun_numero (:laskun-numero rivi)
                        :koontilaskun-kuukausi (kulut-domain/pvm->koontilaskun-kuukausi (:pvm rivi) (:alkupvm urakka))
                        :kokonaissumma (:tavoitehinnan-muutos rivi)}
                  tavoitehinnan-muutos (:tavoitehinnan-muutos rivi)
                  ;; 1.10.2025 ja jälkeen alkavissa urakassa vaaditaan negatiivinen summa näiden muutosten kuluissa
                  _ (when (and
                            (= :vahennys
                              (muutos-domain/jjh-korvaus-muutos-vai-vahennys? (:alkupvm urakka)))
                            (> tavoitehinnan-muutos 0))
                      (throw (Error. "1.10.2025 tai sen jälkeen alkavissa urakoissa Johto- ja hallintokorvausmuutoksen kulut voivat olla vain vähennyksiä eli miinusmerkkisiä.")))
                  ;; jotta saadaan talteen muutoshistoria, aina luodaan uusi kulu ja sen kohdistus, vanhat merkitään poistetuksi
                  kulu-id-db (when (not= tavoitehinnan-muutos 0)
                               (:id (kulu-kyselyt/luo-kulu<! db kulu)))]]

      ;; luodaan aina uusi kohdistus, jos kyseessä "päivitys", poistetaan vanha kohdistus jotta jää historia talteen
      (when kulu-id-db
        (muutos-kyselyt/luo-jjh-kulun-kohdistus<! db
          {:kulu kulu-id-db
           :summa (:tavoitehinnan-muutos rivi)
           :toimenpideinstanssi hoidon-johto-tpi-id
           :kayttaja (:id kayttaja)
           :tyyppi "jjh-muutos"}))

      (cond
        ;; Esim voimassa alkaen pvm muutettiin, rivi muuttui nollaksi -> poista 
        (and (:kulu-id rivi) (= tavoitehinnan-muutos 0))
        (poista-vanhat-kulutiedot! db kayttaja rivi)

        ;; Päivitetään olemassa oleva 
        (and
          kulu-id-db
          (:kulu-id rivi)
          (not= tavoitehinnan-muutos 0))
        (do
          (muutos-kyselyt/paivita-muutos-kulu-linkitys! db {:versio (:versio muutos-id-ja-versio)
                                                            :muutos (:id muutos-id-ja-versio)
                                                            :vanha-kulu (:kulu-id rivi)
                                                            :uusi-kulu kulu-id-db})
          (poista-vanhat-kulutiedot! db kayttaja rivi))

        ;; Tehdään uusi 
        (and kulu-id-db (not (:kulu-id rivi)))
        (do
          (muutos-kyselyt/luo-muutos-kulu-linkitys<! db {:versio (:versio muutos-id-ja-versio)
                                                         :muutos (:id muutos-id-ja-versio)
                                                         :kulu kulu-id-db})
          (poista-vanhat-kulutiedot! db kayttaja rivi)))


      ;; TODO: Korjaa kulun luominen ja päivittäminen, kun teet johto- ja hallintokorvaus muutoksia
      ;;       Pitää pystyä päivittämään vanhaa kulu-riviä siten, että uudet kulutiedot korvaavat vanhat ja versio päivittyy
      ;; FIXME: Tämä on näemmä vielä kesken. Rivin mukana ei tule vielä kulu-id:tä ainakaan testeissä
      )))


(defn- tallenna-muutoksen-liitteet [db aiti-muutos-id-ja-versio liitteet]
  (let [{muutos-id :id uusi-muutos-versio :versio} aiti-muutos-id-ja-versio
        vanhat-liite-idt (set (map :liite
                                (muutos-kyselyt/hae-muutoksen-liite-idt db {:muutos muutos-id})))
        uudet-liite-idt (set (map :id liitteet))
        poistettavat-liite-idt (set/difference vanhat-liite-idt uudet-liite-idt)
        lisattavat-liite-idt (set/difference uudet-liite-idt vanhat-liite-idt)]

    (log/debug " Vanhojen liitteiden idt: " vanhat-liite-idt
      " Uusien liitteiden idt: " uudet-liite-idt
      " Poistettavat liite-idt: " poistettavat-liite-idt
      " Lisättävät liite-idt: " lisattavat-liite-idt)

    ;; Poistetaan vanhat liitteiden linkitykset
    (doseq [liite-id poistettavat-liite-idt]
      (when (and liite-id muutos-id)
        (log/debug "### Poistetaan liite linkitys: " {:muutos muutos-id
                                                      :liite liite-id})
        (muutos-kyselyt/poista-muutos-liite-linkitys! db {:muutos muutos-id
                                                          :liite liite-id})))

    ;; Lisätään uudet liitteet
    (doseq [liite-id lisattavat-liite-idt]
      (when (and liite-id muutos-id uusi-muutos-versio)
        (log/debug "### Lisätään liite linkitys: " {:muutos muutos-id
                                                    :liite liite-id
                                                    :versio uusi-muutos-versio})
        (muutos-kyselyt/linkita-muutos-ja-liite<! db {:muutos muutos-id
                                                      :liite liite-id
                                                      :versio uusi-muutos-versio})))))

(defn luo-kustannusvaikutus
  [aiti-muutos-id versio {:keys [hoitokauden_alkuvuosi toimenpideinstanssi
                                 kustannuslaji summa tehtavamuutoksia syy] :as sql-opts}]
  {:muutos_id aiti-muutos-id
   :versio versio
   :hoitokauden_alkuvuosi hoitokauden_alkuvuosi
   :toimenpideinstanssi toimenpideinstanssi
   :kustannuslaji kustannuslaji
   :summa summa
   :tehtavamuutoksia tehtavamuutoksia
   :syy syy})

(defn tallenna-muutoksen-kustannusvaikutukset
  [db aiti-muutos-id-ja-versio kustannusvaikutukset tyyppi-muutostyo?]
  (log/debug "Tallenna muutoksen kustannusvaikutukset: " kustannusvaikutukset)

  (let [muutos-id (:id aiti-muutos-id-ja-versio)
        muutos-versio (:versio aiti-muutos-id-ja-versio)]
    (doseq [kustannusvaikutus kustannusvaikutukset]
      (let [kustannusvaikutus (luo-kustannusvaikutus muutos-id (or muutos-versio 1) kustannusvaikutus)]
        (if tyyppi-muutostyo?
          (muutos-kyselyt/luo-tai-paivita-erillisrahoitettu-kustannusvaikutus<! db kustannusvaikutus)
          (muutos-kyselyt/luo-tai-paivita-muutos-kustannusvaikutus<! db kustannusvaikutus))))))


(defn luo-tehtava-ja-maaramuutos
  [aiti-muutos-id versio {:keys [tehtava maaramuutos hoitokauden_alkuvuosi] :as sql-opts}]
  {:muutos-id aiti-muutos-id
   :versio versio
   :tehtava tehtava
   :hoitokauden_alkuvuosi hoitokauden_alkuvuosi
   :maaramuutos maaramuutos})

(defn tallenna-muutoksen-tehtavien-maaramuutokset
  "Poikkeaminen tehtävä- ja määräluettelon määristä"
  [db kayttaja-id urakka-id aiti-muutos-id-ja-versio maaramuutokset]
  (log/debug "Tallennetaan tehtävä- ja määrämuutokset: " maaramuutokset)

  (let [kaikki-muutokset maaramuutokset
        muutos-id (:id aiti-muutos-id-ja-versio)
        muutos-versio (:versio aiti-muutos-id-ja-versio)
        poistettavat (filter :poistettu maaramuutokset)
        lisattavat-ja-paivitettavat (remove :poistettu maaramuutokset)]

    ;; Poista rivi, jos se on merkitty poistettavaksi
    (doseq [maaramuutos poistettavat]
      (when (and (:tehtava maaramuutos) (:hoitokauden_alkuvuosi maaramuutos))
        (muutos-kyselyt/poista-tehtavan-maaramuutos! db {:muutos-id muutos-id
                                                         :tehtava (:tehtava maaramuutos)
                                                         :hoitokauden_alkuvuosi (:hoitokauden_alkuvuosi maaramuutos)})))

    ;; Luo tai päivitä rivi
    (doseq [maaramuutos lisattavat-ja-paivitettavat]
      ;; Vain määrämuutokset joilla on positiviinen tehtävän id käsitellään.
      ;; Negatiivisilla id:llä merkityt rivit ovat UI:ssa rivejä, joille ei ole vielä valittu tehtävää
      (when (pos? (:tehtava maaramuutos))
        (let [maaramuutos (luo-tehtava-ja-maaramuutos muutos-id (or muutos-versio 1) maaramuutos)]
          (muutos-kyselyt/luo-tai-paivita-tehtavan-maaramuutos<! db maaramuutos))))

    ;; Päivitä tavoite ja kattohinta 
    (doseq [maaramuutos kaikki-muutokset]
      (when (and (:hoitokauden_alkuvuosi maaramuutos))
        (ks-kyselyt/paivita-tavoite-ja-kattohinta db kayttaja-id urakka-id (:hoitokauden_alkuvuosi maaramuutos)
          (hae-aiempien-vuosien-pysyvat-muutokset db urakka-id (:hoitokauden_alkuvuosi maaramuutos) true))))))


(defn- tarkista-muutoksen-kirjatut-kulut [db {:keys [id voimassa_alkaen] :as muutos} alityyppi kustannusvaikutukset]
  ;; Jos tehdään muutostyö jolle voi kirjata kuluja 
  ;; -> tämän jälkeen vaihdetaan voimassa_alkaen päivää 
  ;; -> pitää tarkistaa voidaanko näin tehdä, esim jos kuluja on jo kirjattu
  ;; Sama tarkastus budjetille (tavoitehinnan muutos)
  (let [tavoitehinnan-muutos (apply + (keep #(:summa %) kustannusvaikutukset))
        jo-kirjatut-kulut (muutos-kyselyt/muutostyolle-jo-kirjatut-kulut-yhteensa db
                            {:muutos id
                             :tyyppi (when (= alityyppi "erillisrahoitus") "erillisrahoitettu-muutos")})

        budjetti-ylittyy? (boolean (when (and tavoitehinnan-muutos jo-kirjatut-kulut)
                                     (> (bigdec jo-kirjatut-kulut) (bigdec tavoitehinnan-muutos))))

        vuosi (when voimassa_alkaen (pvm/vuosi voimassa_alkaen))
        kk (when voimassa_alkaen (pvm/kuukausi voimassa_alkaen))
        paiva (when voimassa_alkaen (pvm/paiva voimassa_alkaen))
        voimassa-alkaen-sql (when voimassa_alkaen (str vuosi "-" kk "-" paiva))
        vastaus (muutos-kyselyt/onko-muutoksella-kuluja-ennen-voimassa-paivaa?
                  db
                  {:muutos id
                   :tyyppi (cond
                             ;; Tähän voi lisätä myös poikkeamatyypin, jos sille voidaan kirjata kuluja
                             (= alityyppi "erillisrahoitus")
                             "erillisrahoitettu-muutos"

                             :else nil)
                   :voimassa voimassa-alkaen-sql})
        kuluja-kirjattu? (boolean vastaus)]

    (cond
      budjetti-ylittyy?
      (throw+ {:type virheet/+viallinen-kutsu+
               :virheet [{:koodi virheet/+sisainen-kasittelyvirhe+
                          :viesti (str
                                    "Muutostyön budjetti ylittyy. "
                                    "Kuluja on jo kirjattu yhteensä " (fmt/euro-opt jo-kirjatut-kulut))}]})

      kuluja-kirjattu?
      (throw+ {:type virheet/+viallinen-kutsu+
               :virheet [{:koodi virheet/+sisainen-kasittelyvirhe+
                          :viesti (str
                                    "Muutostyölle on jo kirjattu kuluja ennen " (pvm/pvm (:voimassa_alkaen muutos)) ". "
                                    "Tarkista kulujen päivämäärät.")}]}))))

(defn- hae-laskutusrajan-konteksti
  "Hakee laskutusrajan laskennassa tarvittavat tiedot.
   Palauttaa mapin, jossa on hoitokausinro, hoitovuoden-tavoitehinta,
   laskutusraja, laskutusraja_alkuperainen, laskutusrajaa_nostettu?,
   muutokset-yhteensa-kaikki ja muutokset-yhteensa-ilman-valittua."
  [conn urakka-id hk-alkuvuosi paivitettava-muutos-id]
  (let [urakan-tiedot (first (q-urakat/hae-urakka conn {:id urakka-id}))
        hoitokausinro (pvm/hoitokausivuosi->mhu-hoitovuosi-nro (:alkupvm urakan-tiedot) hk-alkuvuosi)
        tavoitehinnat (budjettisuunnittelu-q/urakan-tavoitehintojen-tilat conn urakka-id)
        hoitovuoden-tavoitehinta (->> tavoitehinnat
                                   (filter #(= (:hoitokauden-alkuvuosi %) hk-alkuvuosi))
                                   first
                                   :tavoitehinta-indeksikorjattu)
        laskutusraja (:laskutusraja (first (kulu-kyselyt/hae-urakan-laskutusraja conn {:urakka-id urakka-id :hoitokausinro hoitokausinro})))
        laskutusraja-alkuperainen (:laskutusraja_alkuperainen (first (kulu-kyselyt/hae-urakan-alkuperainen-laskutusraja conn {:urakka-id urakka-id :hoitokausinro hoitokausinro})))
        muutokset-yhteensa-kaikki (-> (muutos-kyselyt/hae-laskutusrajan-muutosten-summa-hoitovuodelle
                                        conn {:urakka-id urakka-id :hoitokauden_alkuvuosi hk-alkuvuosi :paivitettava-muutos-id nil})
                                    first :muutokset_yhteensa (or 0))
        muutokset-yhteensa-ilman-valittua (-> (muutos-kyselyt/hae-laskutusrajan-muutosten-summa-hoitovuodelle
                                                conn {:urakka-id urakka-id :hoitokauden_alkuvuosi hk-alkuvuosi :paivitettava-muutos-id paivitettava-muutos-id})
                                            first :muutokset_yhteensa (or 0))]
    {:hoitokausinro hoitokausinro
     :hoitovuoden-tavoitehinta hoitovuoden-tavoitehinta
     :laskutusraja laskutusraja
     :laskutusraja_alkuperainen laskutusraja-alkuperainen
     :laskutusrajaa_nostettu? (when (and laskutusraja laskutusraja-alkuperainen) (> laskutusraja laskutusraja-alkuperainen))
     :muutokset-yhteensa-kaikki muutokset-yhteensa-kaikki
     :muutokset-yhteensa-ilman-valittua muutokset-yhteensa-ilman-valittua}))

(defn tallenna-muutos [db kayttaja {:keys [urakka-id valittu-hoitokausi hoitokaudet muutos laskenta-automatiikka?] :as tiedot}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu kayttaja urakka-id)
  (let [paivitetaan? (:id muutos)
        urakka (first (q-urakat/hae-urakka db urakka-id))
        kulut (:kulut muutos)
        liitteet (:liitteet muutos)
        hk-alkuvuosi (pvm/vuosi (first valittu-hoitokausi))
        ;; Tehtävien määrämuutokset (per tehtävä)
        maaramuutokset (:tehtavat_ja_maarat muutos)
        tavoitehinnan-muutos (:tavoitehinnan-muutos muutos)
        tyyppi-pysyva? (= (:tyyppi muutos) "pysyva")
        tyyppi-muutostyo? (= (:tyyppi muutos) "muutostyo")
        tyyppi-johto-ja-hallinto? (= (:tyyppi muutos) "johto-ja-hallintokorvaus")
        kustannusvaikutukset (cond
                               ;; Pysyvä muutos 
                               tyyppi-pysyva?
                               (:kustannusvaikutukset muutos)

                               ;; Muutostyö
                               tyyppi-muutostyo?
                               (list {:summa tavoitehinnan-muutos
                                      :kustannuslaji "erillishankinnat"
                                      :hoitokauden_alkuvuosi hk-alkuvuosi
                                      :toimenpideinstanssi nil}))

        ;; Alityyppi pelkästään olemassa muutostyöllä 
        alityyppi (when tyyppi-muutostyo? (-> muutos :alityyppi name))

        muutos {:id (:id muutos)
                :versio (:versio muutos)
                :urakka urakka-id
                :voimassa_alkaen (:voimassa_alkaen muutos)
                :nimi (:nimi muutos)
                :syy (:syy muutos)
                :kulu_kohdistus (:kulu-kohdistus muutos)
                :luonnos (:luonnos muutos)
                :hoitokauden_alkuvuosi hk-alkuvuosi
                :tyyppi (:tyyppi muutos)
                :kayttaja (:id kayttaja)
                :alityyppi alityyppi}]

    ;; Validoi voimassa_alkaen päivämäärä
    (cond
      tyyppi-muutostyo?
      (let [pvm-hk-valissa? (boolean (when valittu-hoitokausi
                                       (pvm/valissa?
                                         (:voimassa_alkaen muutos)
                                         (first valittu-hoitokausi)
                                         (second valittu-hoitokausi))))]
        (when (and
                valittu-hoitokausi
                (not pvm-hk-valissa?))
          (throw+ {:type virheet/+viallinen-kutsu+
                   :virheet [{:koodi virheet/+sisainen-kasittelyvirhe+
                              :viesti "Voimassa alkaen täytyy kohdistua valittuun hoitokauteen"}]}))))

    (jdbc/with-db-transaction [conn db]

      (let [vanha-muutos (when paivitetaan?
                           (first (muutos-kyselyt/hae-muutos conn {:id (:id muutos)})))
            tavoitehinta-indeksikorjattu-per-hoitovuosi (urakan-tavoitehinnat-indeksikorjattu db urakka-id)]
        (cond
          tyyppi-muutostyo?
          (tarkista-muutoksen-kirjatut-kulut conn muutos alityyppi kustannusvaikutukset)

          tyyppi-pysyva?
          ;; Estä tallennus, mikäli yritetään muokata lukittua pysyvän muutoksen voimassa_alkaen päivämäärää
          (when (and
                  ;; Huom, vain muokkaustilanteessa tarkistus
                  paivitetaan?
                  (muutos-domain/pysyva-muutos-voimassa-alkaen-lukittu? tavoitehinta-indeksikorjattu-per-hoitovuosi)
                  (not= (:voimassa_alkaen muutos) (:voimassa_alkaen vanha-muutos)))
            (throw+ {:type virheet/+viallinen-kutsu+
                     :virheet [{:koodi virheet/+sisainen-kasittelyvirhe+
                                :viesti "Pysyvän muutoksen voimassa alkaen -päivämäärää ei voi muuttaa, koska se on lukittu."}]})))

        ;; Muutos-id ja muutos-versio kuljetetaan äiti-muutokselta (mhu_muutos-taulu) lapsitauluille
        ;; Nämä tiedot saadaan muutos-paluurivistä
        (let [aiti-muutos-id-ja-versio (if paivitetaan?
                                         (muutos-kyselyt/paivita-muutos<! conn muutos)
                                         (muutos-kyselyt/luo-muutos<! conn muutos))
              {:keys [hoitokausinro hoitovuoden-tavoitehinta laskutusraja laskutusraja_alkuperainen
                      laskutusrajaa_nostettu? muutokset-yhteensa-kaikki muutokset-yhteensa-ilman-valittua]}
              (hae-laskutusrajan-konteksti conn urakka-id hk-alkuvuosi (when paivitetaan? (:id muutos)))
              aiempi-muutos (- muutokset-yhteensa-kaikki muutokset-yhteensa-ilman-valittua)
              summa (:summa (first kustannusvaikutukset))
              muutosten-prosenttiosuus-tavoitehinnasta-uutta-luotaessa
              (when (and hoitovuoden-tavoitehinta summa)
                (tyokalut/pyorista-kahteen-decimaaliin (* 100.00 (/ (+ muutokset-yhteensa-kaikki summa) (double hoitovuoden-tavoitehinta)))))
              muutosten-prosenttiosuus-tavoitehinnasta-muokattaessa
              (when (and hoitovuoden-tavoitehinta summa)
                (tyokalut/pyorista-kahteen-decimaaliin (* 100.00 (/ (+ muutokset-yhteensa-ilman-valittua summa) (double hoitovuoden-tavoitehinta)))))]

          ;; Tallenna liitteet
          (tallenna-muutoksen-liitteet conn aiti-muutos-id-ja-versio liitteet)

          ;; Tallenna kustannusvaikutukset
          (when (pos? (count kustannusvaikutukset))
            (let [kustannusvaikutukset (if tyyppi-pysyva?
                                         ;; Filtteröidään pois kustannusvaikutukset lukituilta hoitovuosilta
                                         ;; Varmistetaan, että lukitulle vuodelle ei tule muutoksia käyttöliittymältä
                                         (filterv #(not (muutos-domain/pysyva-muutos-hoitovuosi-lukittu?
                                                          tavoitehinta-indeksikorjattu-per-hoitovuosi
                                                          (:voimassa_alkaen muutos)
                                                          (pvm/vuodesta-hoitokausi (:hoitokauden_alkuvuosi %))))
                                           kustannusvaikutukset)
                                         kustannusvaikutukset)]
              (tallenna-muutoksen-kustannusvaikutukset conn aiti-muutos-id-ja-versio kustannusvaikutukset tyyppi-muutostyo?)))

          ;; Tallenna määrämuutokset
          (when (pos? (count maaramuutokset))
            (let [maaramuutokset (if tyyppi-pysyva?
                                   ;; Filtteröidään pois maaramuutokset lukituilta hoitovuosilta
                                   ;; Varmistetaan, että lukitulle vuodelle ei tule muutoksia käyttöliittymältä
                                   (filterv #(not (muutos-domain/pysyva-muutos-hoitovuosi-lukittu?
                                                    tavoitehinta-indeksikorjattu-per-hoitovuosi
                                                    (:voimassa_alkaen muutos)
                                                    (pvm/vuodesta-hoitokausi (:hoitokauden_alkuvuosi %))))
                                     maaramuutokset)
                                   maaramuutokset)]
              (tallenna-muutoksen-tehtavien-maaramuutokset conn (:id kayttaja) urakka-id aiti-muutos-id-ja-versio maaramuutokset)))

          (when-let [uusi-laskutusraja (muutos-apurit/laske-uusi-laskutusraja paivitetaan? tyyppi-muutostyo? tyyppi-pysyva?
                                         muutosten-prosenttiosuus-tavoitehinnasta-uutta-luotaessa
                                         muutosten-prosenttiosuus-tavoitehinnasta-muokattaessa laskutusrajaa_nostettu?
                                         laskutusraja laskutusraja_alkuperainen
                                         hoitovuoden-tavoitehinta summa muutokset-yhteensa-kaikki
                                         muutokset-yhteensa-ilman-valittua aiempi-muutos)]
            (kulu-kyselyt/paivita-urakan-laskutusraja! conn
              {:urakka-id urakka-id
               :hoitokausinro hoitokausinro
               :laskutusraja uusi-laskutusraja
               :kayttaja (:id kayttaja)}))

          ;; Tallenna kulut
          (case
            tyyppi-johto-ja-hallinto?
            (tallenna-johto-ja-hallintokorvauksen-muutokset conn kayttaja urakka aiti-muutos-id-ja-versio kulut))))

      ;; Palauta päivitetty listaus
      (hae-urakan-muutostiedot conn kayttaja {:urakka-id urakka-id
                                              :hoitokaudet hoitokaudet
                                              :valittu-hoitokausi valittu-hoitokausi
                                              :laskenta-automatiikka? laskenta-automatiikka?}))))


(defn pysyvan-muutoksen-hoitovuodet
  "Palauttaa listan hoitovuosista, joita pysyvä muutos koskee perustuen syötettyihin kustannusvaikutuksiin."
  [db muutos]
  (let [muutos-id (:id muutos)
        muutos-versio (:versio muutos)
        urakka-id (:urakka muutos)
        kustannusvaikutukset (mapv
                               (fn [rivi]
                                 (-> rivi
                                   (update :kustannusvaikutukset #(konv/jsonb->clojuremap %))))
                               (muutos-kyselyt/hae-pysyvan-muutoksen-kustannustiedot db {:id muutos-id
                                                                                         :versio muutos-versio
                                                                                         :urakka urakka-id}))]
    (->> kustannusvaikutukset
      (mapcat :kustannusvaikutukset)
      (map :hoitokauden_alkuvuosi)
      (distinct)
      (remove nil?))))


(defn voi-poistaa-pysyvan-muutoksen?
  "Palauttaa true, jos pysyvän muutoksen voi poistaa.
  False, jos jokin muutoksen koskema hoitovuosi on lukittu (TODO: tai välikatselmus on tehty)."
  [db tavoitehinta-indeksikorjattu-per-hoitovuosi muutos]

  (let [muutoksen-hk-alkuvuodet (pysyvan-muutoksen-hoitovuodet db muutos)
        ;; Tarkistetaan onko hoitovuoden alun tavoitehinnan kannalta relevantteja vuosia jo lukittu
        lukitut-vuodet (filterv #(muutos-domain/pysyva-muutos-hoitovuosi-lukittu?
                                   tavoitehinta-indeksikorjattu-per-hoitovuosi
                                   (:voimassa_alkaen muutos)
                                   (pvm/vuodesta-hoitokausi %))
                         muutoksen-hk-alkuvuodet)]

    ;; TODO: Välikatselmuksen tarkistus tähän myöhemmin

    ;; Muutoksen saa poistaa, mikäli lukittuja vuosia ei ole
    (empty? lukitut-vuodet)))

(defn muutoksen-poisto-estetty?
  "Palauttaa mapin, jossa :voi-poistaa? boolean ja :virhe string (jos ei voi poistaa)"
  [db tavoitehinta-indeksikorjattu-per-hoitovuosi muutos]
  (case (:tyyppi muutos)
    "pysyva"
    (if (voi-poistaa-pysyvan-muutoksen? db tavoitehinta-indeksikorjattu-per-hoitovuosi muutos)
      {:voi-poistaa? true}
      {:voi-poistaa? false
       :virhe "Muutoksen muuttamia tavoitehintoja on vahvistettu. Peru mahdolliset vahvistukset poistaaksesi muutoksen."})

    "muutostyo"
    ;; TODO: Alityyppi on toisaalla keyword ja toisaalla string, yhtenäistä tämä jossain vaiheessa muutosten kokonaisuudessa
    ;;       Kaikki tyypit (tyyppi/alityyppi) saisivat olla stringejä tai keywordeja joka paikassa, mutta ei sekaisin kumpaakin
    (if (= (:alityyppi muutos) "erillisrahoitus")
      (let [kulujen-maara (or (muutos-kyselyt/hae-muutostyon-kulujen-maara db
                                {:muutos-id (:id muutos)})
                            0)]
        (if (zero? kulujen-maara)
          {:voi-poistaa? true}
          {:voi-poistaa? false
           :virhe "Muutostyölle on kohdistettu kuluja. Poista kohdistetut kulut ennen muutoksen poistamista."}))
      ;; Muut muutostyöt
      {:voi-poistaa? true})

    ;; JJH ja muut ilman erityisiä poistorajoituksia
    {:voi-poistaa? true}))

(defn- hae-jjh-muutoksen-kulut
  "Hakee kaikki JJH-muutokseen liittyvät kulut mhu_muutos_kulu -taulusta."
  [db muutos-id muutos-versio]
  (muutos-kyselyt/hae-jjh-muutoksen-kulut db {:muutos muutos-id
                                              :versio muutos-versio}))

(defn- poista-jjh-muutoksen-kulut!
  "Asettaa poistetuksi JJH-muutoksen automaattisesti luodut kulut ja niiden kohdistukset."
  [db kayttaja muutos-id muutos-versio]
  (let [kulut (hae-jjh-muutoksen-kulut db muutos-id muutos-versio)]
    (doseq [kulu kulut]
      (log/info "Poistetaan JJH-muutoksen kulu id:llä" (:kulu-id kulu))
      (kulu-kyselyt/poista-kulu! db {:id (:kulu-id kulu)
                                     :kayttaja (:id kayttaja)})
      (kulu-kyselyt/poista-kulun-kohdistukset! db {:id (:kulu-id kulu)
                                                   :kayttaja (:id kayttaja)}))))

(defn poista-muutos
  "Poistaa muutoksen ja tarvittaessa muutokseen liittyvät tiedot muutoksen tyypistä riippuen"
  [db kayttaja {:keys [urakka-id valittu-hoitokausi hoitokaudet muutos-id laskenta-automatiikka?] :as tiedot}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu kayttaja urakka-id)

  (jdbc/with-db-transaction [conn db]
    (let [muutos (first (muutos-kyselyt/hae-muutos conn {:id muutos-id}))
          _ (when-not muutos
              (throw+ {:type virheet/+viallinen-kutsu+
                       :virheet [{:koodi virheet/+sisainen-kasittelyvirhe-koodi+
                                  :viesti "Muutosta ei löydy"}]}))

          tavoitehinta-indeksikorjattu-per-hoitovuosi (urakan-tavoitehinnat-indeksikorjattu conn urakka-id)
          ;; Tarkasta voiko muutoksen poistaa
          {:keys [voi-poistaa? virhe]} (muutoksen-poisto-estetty? conn tavoitehinta-indeksikorjattu-per-hoitovuosi muutos)
          hk-alkuvuosi (pvm/vuosi (first valittu-hoitokausi))
          {:keys [hoitokausinro hoitovuoden-tavoitehinta laskutusraja laskutusraja_alkuperainen
                  laskutusrajaa_nostettu? muutokset-yhteensa-kaikki muutokset-yhteensa-ilman-valittua]}
          (hae-laskutusrajan-konteksti conn urakka-id hk-alkuvuosi muutos-id)
          tyyppi-pysyva? (= (:tyyppi muutos) "pysyva")
          tyyppi-muutostyo? (= (:tyyppi muutos) "muutostyo")
          poistettava-muutos (- muutokset-yhteensa-kaikki muutokset-yhteensa-ilman-valittua)
          prosenttiosuus (when hoitovuoden-tavoitehinta
                           (tyokalut/pyorista-kahteen-decimaaliin
                             (* 100.00 (/ muutokset-yhteensa-ilman-valittua (double hoitovuoden-tavoitehinta)))))]

      (when (not voi-poistaa?)
        (throw+ {:type virheet/+sisainen-kasittelyvirhe+
                 :virheet [{:koodi virheet/+sisainen-kasittelyvirhe-koodi+
                            :viesti virhe}]}))

      ;; Poista JJH-muutoksen kulut ennen muutoksen poistoa
      ;; Kulut ovat automaattisesti luotuja, joten ne voidaan poistaa ilman erillistä käyttäjän vahvistusta
      (when (= (:tyyppi muutos) "johto-ja-hallintokorvaus")
        (poista-jjh-muutoksen-kulut! conn kayttaja muutos-id (:versio muutos)))

      ;; Laskutusrajaa pitää päivittää, jos muutosten yhteissumma on yli 3% tavoitehinnasta, ja laskutusrajaa on aiemmin nostettu
      (when (and
              laskutusrajaa_nostettu?
              prosenttiosuus
              (or tyyppi-muutostyo?
                (and tyyppi-pysyva?
                  (not (= muutokset-yhteensa-kaikki muutokset-yhteensa-ilman-valittua)))))
        (kulu-kyselyt/paivita-urakan-laskutusraja! conn
          {:urakka-id urakka-id
           :hoitokausinro hoitokausinro
           :laskutusraja (if (> prosenttiosuus 3.00)
                           (- laskutusraja poistettava-muutos)
                           laskutusraja_alkuperainen)
           :kayttaja (:id kayttaja)}))

      ;; Merkitse muutos poistetuksi
      ;; Äiti-muutos poistetaan soft-deletellä ja linkitetyt taulut jätetään ennalleen
      (muutos-kyselyt/poista-muutos! conn {:id muutos-id
                                           :kayttaja (:id kayttaja)})

      ;; Onnistuneen poiston jälkeen palautetaan ajantasaiset tiedot
      (hae-urakan-muutostiedot conn kayttaja {:urakka-id urakka-id
                                              :hoitokaudet hoitokaudet
                                              :valittu-hoitokausi valittu-hoitokausi
                                              :laskenta-automatiikka? laskenta-automatiikka?}))))

(defn hae-urakan-muutostyot
  "Hakee kululomakkeeseen laaditut muutostyöt, jotta näille voi kirjata kuluja"
  [db kayttaja
   {:keys [urakka-id valittu-hoitokausi] :as _tiedot}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu kayttaja urakka-id)
  (let [hoitokauden-alkuvuosi (pvm/vuosi (first valittu-hoitokausi))
        alkupvm (str hoitokauden-alkuvuosi "-10-01")
        loppupvm (str (inc hoitokauden-alkuvuosi) "-09-30")]
    (muutos-kyselyt/hae-urakan-muutostyot db {:urakka urakka-id
                                              :alkupvm alkupvm
                                              :loppupvm loppupvm})))


(defn tallenna-rahavarausmuutosten-syyt
  "Rahavarausmuutosten syiden tallennus on irtallaan mhu_muutos logiikasta, eikä syiden historiaa tallenneta _historia tauluun."
  [db kayttaja {:keys [urakka-id valittu-hoitokausi hoitokaudet rivit laskenta-automatiikka?]}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu kayttaja urakka-id)
  (let [hoitokauden-alkuvuosi (pvm/vuosi (first valittu-hoitokausi))]
    (jdbc/with-db-transaction [conn db]
      (doseq [{:keys [id syy]} rivit]
        (muutos-kyselyt/upsert-rahavarausmuutosten-syyt!
          conn
          {:urakka urakka-id
           :hoitokauden_alkuvuosi hoitokauden-alkuvuosi
           :rahavaraus_id id
           :syy syy
           :kayttaja (:id kayttaja)}))
      (hae-urakan-muutostiedot conn kayttaja {:urakka-id urakka-id
                                              :hoitokaudet hoitokaudet
                                              :valittu-hoitokausi valittu-hoitokausi
                                              :laskenta-automatiikka? laskenta-automatiikka?}))))


(defrecord Muutos [asetukset]
  component/Lifecycle
  (start [this]
    (when (ominaisuus-kaytossa? :mhu-muutokset)
      (julkaise-palvelut
        (:http-palvelin this)

        :tallenna-muutos
        (fn [kayttaja tiedot]
          (tallenna-muutos (:db this) kayttaja tiedot))

        :poista-muutos
        (fn [kayttaja tiedot]
          (poista-muutos (:db this) kayttaja tiedot))

        :hae-urakan-muutostiedot
        (fn [kayttaja tiedot]
          (hae-urakan-muutostiedot (:db this) kayttaja tiedot))

        :hae-muutoksen-tiedot
        (fn [kayttaja tiedot]
          (hae-muutoksen-tiedot (:db this) kayttaja tiedot))

        :hae-pysyvan-muutoksen-pohjatiedot
        (fn [kayttaja tiedot]
          (hae-pysyvan-muutoksen-pohjatiedot (:db this) kayttaja tiedot))

        :hae-tehtava-maaramuutokset
        (fn [kayttaja tiedot]
          (hae-tehtava-maaramuutokset (:db this) kayttaja tiedot))

        :tallenna-tehtava-maaramuutokset
        (fn [kayttaja tiedot]
          (tallenna-tehtava-maaramuutokset (:db this) kayttaja tiedot))

        :tallenna-maaramuutos-yksikkohinta
        (fn [kayttaja tiedot]
          (tallenna-maaramuutos-yksikkohinta (:db this) kayttaja tiedot))

        :hae-urakan-muutostyot
        (fn [kayttaja tiedot]
          (hae-urakan-muutostyot (:db this) kayttaja tiedot))

        :tallenna-rahavarausmuutosten-syyt
        (fn [kayttaja tiedot]
          (tallenna-rahavarausmuutosten-syyt (:db this) kayttaja tiedot))))
    this)

  (stop [this]
    (poista-palvelut (:http-palvelin this)
      :tallenna-muutos
      :poista-muutos
      :hae-muutoksen-tiedot
      :hae-urakan-muutostyot
      :hae-urakan-muutostiedot
      :hae-tehtava-maaramuutokset
      :tallenna-tehtava-maaramuutokset
      :tallenna-maaramuutos-yksikkohinta
      :tallenna-rahavarausmuutosten-syyt)
    this))
