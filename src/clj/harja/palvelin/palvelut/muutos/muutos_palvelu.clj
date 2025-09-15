(ns harja.palvelin.palvelut.muutos.muutos-palvelu
  (:require [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [com.stuartsierra.component :as component]

            [harja.pvm :as pvm]
            [harja.domain.mhu :as mhu]
            [harja.kyselyt.konversio :as konv]
            [harja.kyselyt.urakat :as q-urakat]
            [harja.domain.oikeudet :as oikeudet]
            [harja.tyokalut.yleiset :as yleiset]
            [harja.domain.kulut :as kulut-domain]
            [harja.kyselyt.kulut :as kulu-kyselyt]
            [harja.kyselyt.liitteet :as liite-kyselyt]
            [harja.domain.muutos-domain :as muutos-domain]
            [harja.kyselyt.toimenpideinstanssit :as tpi-q]
            [harja.kyselyt.muutos-kyselyt :as muutos-kyselyt]
            [harja.kyselyt.rahavaraukset :as rahavaraus-kyselyt]
            [harja.kyselyt.tehtavamaarat :as tehtavamaarat-kyselyt]
            [harja.palvelin.asetukset :refer [ominaisuus-kaytossa?]]
            [harja.kyselyt.budjettisuunnittelu :as budjettisuunnittelu-q]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelut poista-palvelut]]))


(defn tavoitehinnan-muutos
  "Laskee rivin tavoitehinnan muutoksen. Sen sijainti vaihtelee tyyppikohtaisesti."
  ;; on hyvä saada tavoitehinnan muutos samaan avaimeen, niin summauslaskennat jne. toimivat myöhemmin suoraan
  [muutokset]
  (mapv (fn [rivi]
          (let [total (if (= (:tyyppi rivi)
                            "johto-ja-hallintokorvaus")
                        (or (:jjh-muutosten-summa rivi) 0)
                        (some->>
                          (:kustannusvaikutukset rivi)
                          (map :summa)
                          (reduce + 0)))]
            (assoc rivi :tavoitehinnan-muutos total)))
    muutokset))

(defn- rahavarausten-summarivi [rahavaraukset]
  (let [{:keys [summa-indeksikorjattu toteumat]}
        (reduce (fn [acc {:keys [summa-indeksikorjattu toteumat]}]
                  {:summa-indeksikorjattu (+ (:summa-indeksikorjattu acc 0)
                                            (or summa-indeksikorjattu 0))
                   :toteumat (+ (:toteumat acc 0)
                               (or toteumat 0))})
          {}
          rahavaraukset)]
    {:id :yhteenveto
     :summa-indeksikorjattu summa-indeksikorjattu
     :toteumat toteumat
     :tavoitehinnan-muutos (- toteumat summa-indeksikorjattu)}))


(defn hae-hoitovuosien-yksikkohinnat
  [db _kayttaja {:keys [urakka-id hoitokaudet tehtava_id] :as _tiedot}]
  (map-indexed (fn [idx valittu-hoitokausi]
                 (let [hoitokauden-alkuvuosi (pvm/vuosi (first valittu-hoitokausi))
                       alkupvm (str hoitokauden-alkuvuosi "-10-01")
                       loppupvm (str (inc hoitokauden-alkuvuosi) "-09-30")
                       params {:urakka urakka-id
                               :tehtavaryhma nil
                               :alkupvm alkupvm
                               :loppupvm loppupvm
                               :tehtava (or tehtava_id nil)
                               :hoitokauden_alkuvuosi hoitokauden-alkuvuosi}
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
                    :hoitokauden-alkuvuosi hoitokauden-alkuvuosi}))
    hoitokaudet))


(defn hae-tehtava-maaramuutokset
  [db
   {:keys [id] :as kayttaja}
   {:keys [urakka-id valittu-hoitokausi hoitokaudet] :as _tiedot}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu kayttaja urakka-id)
  (let [hoitokauden-alkuvuosi (pvm/vuosi (first valittu-hoitokausi))
        alkupvm (str hoitokauden-alkuvuosi "-10-01")
        loppupvm (str (inc hoitokauden-alkuvuosi) "-09-30")
        params {:tehtava nil
                :tehtavaryhma nil
                :urakka urakka-id
                :alkupvm alkupvm
                :loppupvm loppupvm
                :hoitokauden_alkuvuosi hoitokauden-alkuvuosi}
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
                                            suunniteltu_maara
                                            kirjatut_kulut_summa
                                            yksikkohinnan_alkuvuosi] :as rivi}]

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
                                       rivi (if (and
                                                  (> maara 0) ;; tälle vuodelle olemassa toteumia 
                                                  yksikkohinnan_alkuvuosi) ;; mutta yksikköhinta asetettu? => päivitä kanta 
                                              (do
                                                (muutos-kyselyt/paivita-tehtava-maaramuutos<! db {:syy syy
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
                                       kaikki-yksikkohinnat (when tehtavalla-ei-toteumia?
                                                              (hae-hoitovuosien-yksikkohinnat db kayttaja {:urakka-id urakka-id
                                                                                                           :hoitokaudet hoitokaudet
                                                                                                           :tehtava_id (:tehtava_id rivi)}))
                                       ;; Jos edellisiltä vuosilta löytyi yksikköhinta, tarjotaan niitä gridiin 
                                       aikaisemmat-yksikkohinnat (filter #(and
                                                                            ;; Vaadi että jokin yksikköhinta saatavilla 
                                                                            (some? (:arvo %))
                                                                            ;; Suodata kuluva hk pois, tulee mukaan esim jos yksikköhinnan lähde on valittu  
                                                                            (not= hoitokauden-alkuvuosi (:hoitokauden-alkuvuosi %))) kaikki-yksikkohinnat)

                                       loytyi-aikaisemmat-yksikkohinnat? (some? (seq aikaisemmat-yksikkohinnat))

                                       ;; Jos ei edellisiä yksikköhintojakaan löytynyt, anna kirjata tavoitehinta manuaalisesti 
                                       anna-kirjata-tavoitehinta? (and
                                                                    tehtavalla-ei-toteumia?
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
                                                              (* (- maara suunniteltu_maara) yksikkohinta))]

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
                        (> tavoitehinnan_muutos 0))
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
          (muutos-kyselyt/paivita-tehtava-maaramuutos<! conn params))))

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
        ;; (yksikköhinta on asetettu edellisiltä hoitokausilta)
        lahde "valittu"
        hoitokauden-alkuvuosi (pvm/vuosi (first valittu-hoitokausi))
        params {:syy syy
                :lahde lahde
                :kayttaja id
                :urakka urakka-id
                :tehtava tehtava_id
                :kasin_syotetty_tavoitehinta nil
                :hk_alkuvousi hoitokauden-alkuvuosi
                :yksikkohinta_hk_alkuvuosi yksikkohinnan_alkuvuosi}]
    (muutos-kyselyt/paivita-tehtava-maaramuutos<! db params)
    (hae-tehtava-maaramuutokset db kayttaja (assoc params
                                              :urakka-id urakka-id
                                              :hoitokaudet hoitokaudet
                                              :valittu-hoitokausi valittu-hoitokausi))))


(defn hae-urakan-muutostiedot
  [db kayttaja {:keys [urakka-id hoitokaudet valittu-hoitokausi] :as tiedot}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu kayttaja urakka-id)
  (log/debug "hae-urakan-muutostiedot: " tiedot)
  (let [hoitokauden-alkuvuosi (pvm/vuosi (first valittu-hoitokausi))
        kirjatut-muutokset-vastaus (mapv
                                     (fn [rivi]
                                       (-> rivi
                                         (update :kustannusvaikutukset #(konv/jsonb->clojuremap %))
                                         (update :tehtavat_ja_maarat #(konv/jsonb->clojuremap %))
                                         (update :liitteet #(konv/jsonb->clojuremap %))))
                                     (muutos-kyselyt/hae-urakan-hoitovuoden-muutostiedot db {:urakka urakka-id
                                                                                             :hoitokauden_alkuvuosi hoitokauden-alkuvuosi}))
        kirjatut-muutokset (tavoitehinnan-muutos kirjatut-muutokset-vastaus)
        rahavarausten-suunnitelmat (map
                                     #(select-keys % [:id :nimi :summa-indeksikorjattu])
                                     (rahavaraus-kyselyt/hae-urakan-suunnitellut-rahavarausten-kustannukset db {:urakka_id urakka-id
                                                                                                               ;; haetaan vain valitulle hoitovuodelle
                                                                                                                :alkuvuosi hoitokauden-alkuvuosi
                                                                                                                :loppuvuosi (inc hoitokauden-alkuvuosi)}))
        rahavarausten-toteumat (muutos-kyselyt/rahavarausten-toteumat db {:urakka urakka-id
                                                                          :hoitokauden_alkuvuosi hoitokauden-alkuvuosi})
        rahavaraukset (yleiset/yhdista-mapit-avaimella rahavarausten-suunnitelmat rahavarausten-toteumat :id)
        rahavarausmuutosten-syyt (muutos-kyselyt/rahavarausmuutosten-syyt db {:urakka urakka-id
                                                                              :hoitokauden_alkuvuosi hoitokauden-alkuvuosi})
        rahavaraukset (yleiset/yhdista-mapit-avaimella rahavaraukset rahavarausmuutosten-syyt :id)
        rahavaraukset (mapv
                        ;; lasketaan erotus vain jos molemmat arvot ovat olemassa
                        #(if (and (:summa-indeksikorjattu %)
                               (:toteumat %))
                           (assoc % :tavoitehinnan-muutos (- (:toteumat %)
                                                            (:summa-indeksikorjattu %)))
                           %)
                        rahavaraukset)
        rahavaraukset-yhteensa (rahavarausten-summarivi rahavaraukset)
        rahavaraukset (conj rahavaraukset rahavaraukset-yhteensa)
        budjettitavoiteet (budjettisuunnittelu-q/budjettitavoite-vuodelle db urakka-id hoitokauden-alkuvuosi)

        ;; Tehtävä- ja määrätoteumiin perustuvat tavoitehintamuutokset
        tehtava-ja-maaramuutokset (hae-tehtava-maaramuutokset db kayttaja {:urakka-id urakka-id
                                                                           :hoitokaudet hoitokaudet
                                                                           :valittu-hoitokausi valittu-hoitokausi})

        muutosten-vaikutus-yhteensa (reduce + 0
                                      (remove nil?
                                        (concat
                                          (map :tavoitehinnan-muutos kirjatut-muutokset)
                                          [(:tavoitehinnan-muutos (last rahavaraukset))]
                                          (map :tavoitehinnan_muutos tehtava-ja-maaramuutokset))))]

    {;; kirjatut muutokset jos hoitokausi 2025-2026 tai jälkeen
     :kirjatut-muutokset kirjatut-muutokset
     ;; laskennat lasketuille muutoksille jos hoitokausi 2025-2026 tai jälkeen
     :lasketut-muutokset tehtava-ja-maaramuutokset
     :rahavarausten-muutokset rahavaraukset
     ;; TODO: laskennat vanhojen tavoitehintojen muutoksille jos hoitokausi ennen 2025-2026
     :tavoitehinnan-muutokset []
     ;; TODO: laskennat vanhojen suunniteltujen määrien muutoksille jos hoitokausi ennen 2025-2026
     :suunniteltujen-maarien-muutokset []
     :budjettitavoitteet {:indeksikorjaus-vahvistettu? (:indeksikorjaus-vahvistettu budjettitavoiteet)
                          :hoitovuoden-alun-indeksikorjattu-tavoitehinta (:tavoitehinta-indeksikorjattu budjettitavoiteet)
                          :muutosten-vaikutus-yhteensa muutosten-vaikutus-yhteensa
                          :hoitovuoden-lopun-tavoitehinta (when (:tavoitehinta-indeksikorjattu budjettitavoiteet)
                                                            (+
                                                              (:tavoitehinta-indeksikorjattu budjettitavoiteet)
                                                             ;; TODO: tässä huomioitava kaikkien muutosten vaikutus, työversiossa vasta kirjatut muutokset mukana
                                                              muutosten-vaikutus-yhteensa))}}))



(defn hae-mhu-suunniteltavat-tehtavat [db urakka-id alkupvm loppupvm]
  (tehtavamaarat-kyselyt/mhu-suunniteltavat-tehtavat db {:urakka urakka-id
                                                         :hoitokausi (range (pvm/vuosi alkupvm)
                                                                       (inc (pvm/vuosi loppupvm)))}))


;; TODO: Refaktoroi koodia. Tässä on paljon tyypin perusteella iffittelyä, joka menee helposti hankalalukuiseksi
;;       Mieti uudestaan miten muutostyypin perusteella kannattaa lomakkeen perustietoja hakea
;;       Olemassaolevaa muutosta muokatessa on myös tarpeen hakea muutoksen id:n perusteella lisää tietoja
(defn hae-muutoksen-tiedot
  "Palauttaa yksittäisen muutoksen tarkat tiedot lomaketta varten."
  [db kayttaja {:keys [urakka-id hoitokauden-alkuvuosi muutos] :as tiedot}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu kayttaja urakka-id)

  ;; TODO: Tätä tietoa tarvitaan aina pysyvän muutoksen lomakkeella tehtiinpä uutta muutosta tai muokattiinpa vanhaa
  ;;       Tieto generoi taulukon rivit pysyvän muutoksen lomakkeelle
  ;;       Tutki kyselyä uuden muutoksen luonnin näkökulmasta
  (let [toimenpiteiden-tiedot (when (= (:tyyppi muutos) "pysyva")
                                (mapv
                                  (fn [rivi]
                                    (-> rivi
                                      (update :budjetoidut_summat #(konv/jsonb->clojuremap %))
                                      (update :kustannusvaikutukset #(konv/jsonb->clojuremap %))
                                      (update :tehtavat_ja_maarat #(konv/jsonb->clojuremap %))))
                                  ;; pysyvän muutoksen tietoja voi olla usealla hoitovuodella. Kysely ja palvelu palauttavat kaikkien hoitovuosien tiedot, toimenpiteittäin ryhmiteltynä.
                                  (muutos-kyselyt/hae-pysyvan-muutoksen-kustannustiedot db {:id (:id muutos)
                                                                                            :versio (:versio muutos)
                                                                                            :urakka urakka-id
                                                                                            :hoitokauden_alkuvuosi hoitokauden-alkuvuosi})))
        {:keys [alkupvm loppupvm]} (first (q-urakat/hae-urakka db {:id urakka-id}))

        ;; TODO: Tätä tietoa tarvitaan aina pysyvän muutoksen lomakkeella tehtiinpä uutta muutosta tai muokattiinpa vanhaa
        ;;       Tieto generoi taulukon rivit pysyvän muutoksen lomakkeelle
        toimenpiteiden-tehtavat (when (= (:tyyppi muutos) "pysyva")
                                  (map
                                    #(select-keys % #{:jarjestys :tehtava-id :suunniteltu-maara :toimenpidekoodi :tehtava :yksikko :hoitokauden-alkuvuosi})
                                    (hae-mhu-suunniteltavat-tehtavat db urakka-id alkupvm loppupvm)))
        tyyppikohtaiset-tiedot (case (:tyyppi muutos)
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

        _ (when (> (count tyyppikohtaiset-tiedot)  1)
            (log/error "Muutoksia palautui lomakkeelle enemmän kuin yksi urakassa " urakka-id)
            (throw (Error. "Muutoksia palautui enemmän kuin yksi, kyseessä on luultavasti ongelmatilanne. Ota yhteys Harja-palautteeseen.")))
        liitteet (when-not (empty? (:liite-idt muutos))
                   (liite-kyselyt/hae-liitteiden-tiedot db {:idt (:liite-idt muutos)
                                                            :urakka urakka-id}))
        vastaus (assoc (merge muutos
                         (first tyyppikohtaiset-tiedot)
                         (when (= (:tyyppi muutos) "pysyva")
                           {:toimenpiteiden-tiedot toimenpiteiden-tiedot
                            :toimenpiteiden-tehtavat toimenpiteiden-tehtavat}))
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
  (log/debug "Tallennetaan johto- ja hallintokorvauksen muutokset: " rivit " muutos-id-ja-versio" muutos-id-ja-versio)
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
                  ;; 1.10.2025 ja jälkeen alkavissa urakassa vaaditaan negatiivinen summa näiden muutosten kuluissa
                  _ (when (and
                            (= :vahennys
                              (muutos-domain/jjh-korvaus-muutos-vai-vahennys? (:alkupvm urakka)))
                            (> (:tavoitehinnan-muutos rivi) 0))
                      (throw (Error. "1.10.2025 tai sen jälkeen alkavissa urakoissa Johto- ja hallintokorvausmuutoksen kulut voivat olla vain vähennyksiä eli miinusmerkkisiä.")))
                  ;; jotta saadaan talteen muutoshistoria, aina luodaan uusi kulu ja sen kohdistus, vanhat merkitään poistetuksi
                  kulu-id-db (:id (kulu-kyselyt/luo-kulu<! db kulu))
                  ;; luodaan aina uusi kohdistus, jos kyseessä "päivitys", poistetaan vanha kohdistus jotta jää historia talteen
                  kulu-kohdistus-db (muutos-kyselyt/luo-jjh-kulun-kohdistus<! db
                                      {:kulu kulu-id-db
                                       :summa (:tavoitehinnan-muutos rivi)
                                       :toimenpideinstanssi hoidon-johto-tpi-id
                                       :kayttaja (:id kayttaja)
                                       :tyyppi "jjh-muutos"})]]

      ;; TODO: Korjaa kulun luominen ja päivittäminen, kun teet johto- ja hallintokorvaus muutoksia
      ;;       Pitää pystyä päivittämään vanhaa kulu-riviä siten, että uudet kulutiedot korvaavat vanhat ja versio päivittyy
      ;; FIXME: Tämä on näemmä vielä kesken. Rivin mukana ei tule vielä kulu-id:tä ainakaan testeissä
      (if (:kulu-id rivi)
        (muutos-kyselyt/paivita-muutos-kulu-linkitys! db {:versio (:versio muutos-id-ja-versio)
                                                          :muutos (:id muutos-id-ja-versio)
                                                          :vanha-kulu (:kulu-id rivi)
                                                          :uusi-kulu kulu-id-db})
        (muutos-kyselyt/luo-muutos-kulu-linkitys<! db {:versio (:versio muutos-id-ja-versio)
                                                       :muutos (:id muutos-id-ja-versio)
                                                       :kulu kulu-id-db}))
      (poista-vanhat-kulutiedot! db kayttaja rivi))))


(defn- poista-muutos
  "Poistaa muutoksen."
  ;; Hox: tätä ei vielä käytetä. Jossain kohti tulee varmasti lomakkeelle poistaminen mahdolliseksi
  [db kayttaja muutos]
  (when (and (:id muutos) (:versio muutos))
    (log/debug "Poistetaan muutos id:llä " (:id muutos))
    (muutos-kyselyt/poista-muutos! db {:id (:id muutos)
                                       :versio (:versio muutos)
                                       :kayttaja (:id kayttaja)})))

(defn- tallenna-muutoksen-liitteet [db aiti-muutoksen-paluurivi liitteet]
  (doseq [liite liitteet]
    (let [liite-id (:id liite)
          muutos-id (:id aiti-muutoksen-paluurivi)
          muutos-versio (:versio aiti-muutoksen-paluurivi)]
      (when (and liite-id muutos-id muutos-versio)
        (muutos-kyselyt/linkita-muutos-ja-liite<! db {:muutos muutos-id
                                                      :liite liite-id
                                                      :versio muutos-versio})))))

(defn- tallenna-muutoksen-kustannusvaikutukset [db aiti-muutoksen-paluurivi hoitokauden_alkuvuosi kustannusvaikutukset]
  (let [muutos-id (:id aiti-muutoksen-paluurivi)
        muutos-versio (:versio aiti-muutoksen-paluurivi)]
    (doseq [kustannusvaikutus kustannusvaikutukset]
      (let [kustannusvaikutus (assoc kustannusvaikutus
                                :muutos-id muutos-id
                                :versio (or muutos-versio 1)
                                :hoitokauden_alkuvuosi hoitokauden_alkuvuosi)]
        (muutos-kyselyt/luo-tai-paivita-muutos-kustannusvaikutus<! db kustannusvaikutus)))))

(defn tallenna-muutos [db kayttaja {:keys [urakka-id valittu-hoitokausi hoitokaudet muutos] :as tiedot}]
  (log/debug "tallenna-muutos: " tiedot)
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu kayttaja urakka-id)
  (let [urakka (first (q-urakat/hae-urakka db urakka-id))
        kulut (:kulut muutos)
        liitteet (:liitteet muutos)
        tavoitehinnan-muutos (:tavoitehinnan-muutos muutos)
        alityyppi (when (= (:tyyppi muutos) "muutostyo")
                    (-> muutos :alityyppi name))
        muutos {:id (:id muutos)
                :versio (:versio muutos)
                :urakka urakka-id
                :voimassa_alkaen (:voimassa_alkaen muutos)
                :nimi (:nimi muutos)
                :syy (:syy muutos)
                :kulu_kohdistus (:kulu-kohdistus muutos)
                :luonnos (:luonnos muutos)
                :hoitokauden_alkuvuosi (pvm/vuosi (first valittu-hoitokausi))
                :tyyppi (:tyyppi muutos)
                :kayttaja (:id kayttaja)
                :alityyppi alityyppi}
        kustannusvaikutukset (:kustannusvaikutukset muutos)]

    (jdbc/with-db-transaction [conn db]
      ;; Muutos-id ja muutos-versio kuljetetaan äiti-muutokselta (mhu_muutos-taulu) lapsitauluille
      ;; Nämä tiedot saadaan muutos-paluurivistä
      (let [muutos-paluurivi (if (:id muutos)
                               (muutos-kyselyt/paivita-muutos<! conn muutos)
                               (muutos-kyselyt/luo-muutos<! conn muutos))]

        ;; Tallenna liitteet
        (when (pos? (count liitteet))
          (tallenna-muutoksen-liitteet conn muutos-paluurivi liitteet))

        ;; Tallenna kustannusvaikutukset
        (when (pos? (count kustannusvaikutukset))
          (tallenna-muutoksen-kustannusvaikutukset conn muutos-paluurivi (pvm/vuosi (first valittu-hoitokausi)) kustannusvaikutukset))

        ;; Tallenna kulut
        (case
          (= (:tyyppi muutos) "johto-ja-hallintokorvaus")
          (tallenna-johto-ja-hallintokorvauksen-muutokset conn kayttaja urakka muutos-paluurivi kulut)))
      
      ;; Palauta päivitetty listaus 
      (hae-urakan-muutostiedot conn kayttaja {:urakka-id urakka-id
                                              :hoitokaudet hoitokaudet
                                              :valittu-hoitokausi valittu-hoitokausi}))))


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
  [db kayttaja {:keys [urakka-id valittu-hoitokausi hoitokaudet rivit]}]
  (log/debug "Tallenna rahavarausmuutosten syyt" urakka-id valittu-hoitokausi rivit)
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
                                              :valittu-hoitokausi valittu-hoitokausi}))))


(defrecord Muutos [asetukset]
  component/Lifecycle
  (start [this]
    (when (ominaisuus-kaytossa? :mhu-muutokset)
      (julkaise-palvelut
        (:http-palvelin this)

        :hae-urakan-muutostiedot
        (fn [kayttaja tiedot]
          (hae-urakan-muutostiedot (:db this) kayttaja tiedot))

        :hae-muutoksen-tiedot
        (fn [kayttaja tiedot]
          (hae-muutoksen-tiedot (:db this) kayttaja tiedot))

        :hae-tehtava-maaramuutokset
        (fn [kayttaja tiedot]
          (hae-tehtava-maaramuutokset (:db this) kayttaja tiedot))

        :tallenna-tehtava-maaramuutokset
        (fn [kayttaja tiedot]
          (tallenna-tehtava-maaramuutokset (:db this) kayttaja tiedot))

        :tallenna-maaramuutos-yksikkohinta
        (fn [kayttaja tiedot]
          (tallenna-maaramuutos-yksikkohinta (:db this) kayttaja tiedot))

        :tallenna-muutos
        (fn [kayttaja tiedot]
          (tallenna-muutos (:db this) kayttaja tiedot))
        
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
      :hae-muutoksen-tiedot
      :hae-urakan-muutostiedot
      :hae-tehtava-maaramuutokset
      :tallenna-tehtava-maaramuutokset
      :tallenna-maaramuutos-yksikkohinta
      :tallenna-rahavarausmuutosten-syyt)
    this))
