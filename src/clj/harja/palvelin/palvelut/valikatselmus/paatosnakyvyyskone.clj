(ns harja.palvelin.palvelut.valikatselmus.paatosnakyvyyskone
  (:require [clojure.string :as str]
            [harja.pvm :as pvm]
            [harja.tyokalut.yleiset :refer [round2]]
            [harja.kyselyt.urakat :as urakka-kyselyt]
            [harja.kyselyt.lupaus-kyselyt :as lupaus-kyselyt]
            [harja.domain.lupaus-domain :as lupaus-domain]))

(def paatostyypit
  [{:nimi "Lupaukset" :tyyppi "bonus" :urakan_alkuvuosi 2019 :nakyvyys_alkaen 2019 :hoitotyyppi #{"MHU" "MHU+"} :jarjestys 1 :paatostyyppi "lupaus"}
   {:nimi "Lupaukset" :tyyppi "sanktio" :urakan_alkuvuosi 2019 :nakyvyys_alkaen 2019 :hoitotyyppi #{"MHU" "MHU+"} :jarjestys 1 :paatostyyppi "lupaus"}
   {:nimi "Lupaukset" :tyyppi "taytetty" :urakan_alkuvuosi 2019 :nakyvyys_alkaen 2019 :hoitotyyppi #{"MHU" "MHU+"} :jarjestys 1 :paatostyyppi "lupaus"}
   {:nimi "Tavoitehinnan muutokset" :urakan_alkuvuosi 2019 :nakyvyys_alkaen 2019 :nakyvyys_asti 2024 :hoitotyyppi #{"MHU"} :jarjestys 2 :paatostyyppi "tavoitehinnan-muutokset"}
   {:nimi "Tavoitehinnan muutokset" :urakan_alkuvuosi 2021 :nakyvyys_alkaen 2021 :nakyvyys_asti 2028 :hoitotyyppi #{"MHU"} :jarjestys 2 :paatostyyppi "tavoitehinnan-muutokset"}
   {:nimi "Tavoitehinnan muutokset" :urakan_alkuvuosi 2024 :nakyvyys_alkaen 2024 :nakyvyys_asti 2024 :hoitotyyppi #{"MHU+"} :jarjestys 2 :paatostyyppi "tavoitehinnan-muutokset"}
   {:nimi "Tavoitehinnan pysyvät muutokset" :urakan_alkuvuosi 2025 :nakyvyys_alkaen 2025 :hoitotyyppi #{"MHU" "MHU+"} :jarjestys 2 :paatostyyppi "tavoitehinnan-pysyvat-muutokset"}
   {:nimi "Hoitovuoden lopun indeksikorjaus" :tyyppi nil :urakan_alkuvuosi 2024 :nakyvyys_alkaen 2024 :hoitotyyppi #{"MHU" "MHU+"} :jarjestys 3 :paatostyyppi "indeksikorjaus"}
   {:nimi "Hoitovuoden lopun tavoite- ja kattohinta" :tyyppi "A" :urakan_alkuvuosi 2021 :nakyvyys_alkaen 2024 :nakyvyys_asti 2024 :hoitotyyppi #{"MHU"} :jarjestys 4 :paatostyyppi "hoitovuoden-lopun-hinta"}
   {:nimi "Hoitovuoden lopun tavoite- ja kattohinta" :tyyppi "B" :urakan_alkuvuosi 2024 :nakyvyys_alkaen 2024 :nakyvyys_asti 2024 :hoitotyyppi #{"MHU"} :jarjestys 4 :paatostyyppi "hoitovuoden-lopun-hinta"}
   {:nimi "Hoitovuoden lopun tavoite- ja kattohinta" :tyyppi "B" :urakan_alkuvuosi 2024 :nakyvyys_alkaen 2024 :hoitotyyppi #{"MHU+"} :jarjestys 4 :paatostyyppi "hoitovuoden-lopun-hinta-v2"}
   {:nimi "Hoitovuoden lopun tavoite- ja kattohinta" :tyyppi "C" :urakan_alkuvuosi 2025 :nakyvyys_alkaen 2025 :hoitotyyppi #{"MHU"} :jarjestys 4 :paatostyyppi "hoitovuoden-lopun-hinta-v2"}
   {:nimi "Tavoitehinnan alitus" :urakan_alkuvuosi 2019 :nakyvyys_alkaen 2019 :hoitotyyppi #{"MHU"} :jarjestys 5 :paatostyyppi "tavoitehinta"}
   {:nimi "Tavoitehinnan alitus" :urakan_alkuvuosi 2024 :nakyvyys_alkaen 2024 :hoitotyyppi #{"MHU+"} :jarjestys 5 :paatostyyppi "tavoitehinta"}
   {:nimi "Tavoitehinnan ylitys" :tyyppi "A" :urakan_alkuvuosi 2019 :nakyvyys_alkaen 2019 :hoitotyyppi #{"MHU"} :jarjestys 6 :paatostyyppi "tavoitehinta"}
   {:nimi "Tavoitehinnan ylitys" :tyyppi "B" :urakan_alkuvuosi 2024 :nakyvyys_alkaen 2019 :hoitotyyppi #{"MHU" "MHU+"} :jarjestys 6 :paatostyyppi "tavoitehinta"}
   {:nimi "Kattohinnan ylitys" :urakan_alkuvuosi 2019 :nakyvyys_alkaen 2019 :hoitotyyppi #{"MHU"} :jarjestys 7 :paatostyyppi "kattohinta"}
   {:nimi "Kattohinnan ylitys" :urakan_alkuvuosi 2024 :nakyvyys_alkaen 2024 :hoitotyyppi #{"MHU+"} :jarjestys 7 :paatostyyppi "kattohinta"}
   {:nimi "Hoidonjohtopalkkion muutos" :urakan_alkuvuosi 2021 :nakyvyys_alkaen 2024 :hoitotyyppi #{"MHU"} :jarjestys 8 :paatostyyppi "hoidonjohtopalkkio"}
   {:nimi "Hoidonjohtopalkkion muutos" :urakan_alkuvuosi 2024 :nakyvyys_alkaen 2024 :hoitotyyppi #{"MHU" "MHU+"} :jarjestys 8 :paatostyyppi "hoidonjohtopalkkio"}
   {:nimi "Välikatselmuspöytäkirjaan liitettävät raportit" :urakan_alkuvuosi 2020 :nakyvyys_alkaen 2024 :hoitotyyppi #{"MHU"} :jarjestys 9 :paatostyyppi "raportti"}
   {:nimi "Välikatselmuspöytäkirjaan liitettävät raportit" :urakan_alkuvuosi 2024 :nakyvyys_alkaen 2024 :hoitotyyppi #{"MHU" "MHU+"} :jarjestys 9 :paatostyyppi "raportti"}])

(defn distinct-by [vektori avain]
  (:result (reduce
             (fn [{:keys [seen result]} m]
               (let [arvo (get-in m [avain])]
                 (if (contains? seen arvo)
                   {:seen seen :result result}
                   {:seen (conj seen arvo) :result (conj result m)})))
             {:seen #{}, :result []}
             vektori)))

(defn urakan-hoitotyyppi
  "Erittäin vaativat hoitourakat merkitään päätöstauluun hoitotyyppinä MHU+"
  [erittain_vaativa_hoitourakka]
  (if erittain_vaativa_hoitourakka "MHU+" "MHU"))

(defn mahdolliset-paatokset-tyypilla [mhu-tyyppi paatokset]
  (filter #(contains? (:hoitotyyppi %) mhu-tyyppi) paatokset))

(defn mahdolliset-paatokset-urakan-alkuvuodella [urakan-alkuvuosi paatokset]
  (filter (fn [paatos]
            (<= (:urakan_alkuvuosi paatos) urakan-alkuvuosi))
    paatokset))

(defn mahdolliset-paatokset-nakyvyys-asti [urakan-alkuvuosi paatokset]
  (filter #(or (nil? (:nakyvyys_asti %))
             (and (:nakyvyys_asti %) (>= (:nakyvyys_asti %) urakan-alkuvuosi))) paatokset))

(defn mahdolliset-paatokset-nakyvyys-vuodella [kuluva-vuosi paatokset]
  (filter #(<= (:nakyvyys_alkaen %) kuluva-vuosi) paatokset))

(defn vain-yksi-paatos-per-tyyppi [paatokset]
  (let [uniikit-tyypit (map (fn [paatos]
                              (assoc paatos :uniikki-tyyppi (str (:tyyppi paatos) (:nimi paatos)))) paatokset)
        uniikit (distinct-by uniikit-tyypit :uniikki-tyyppi)
        paatokset (map (fn [paatos]
                         (dissoc paatos :uniikki-tyyppi)) uniikit)]
    paatokset))

(defn kaikki-mahdolliset-paatokset [mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi kuluva-hoitovuosi]
  (let [mahdollset-tyypilla (mahdolliset-paatokset-tyypilla mhu-tyyppi paatostyypit)
        mahdolliset-aloitusvuodella (mahdolliset-paatokset-urakan-alkuvuodella urakan-alkuvuosi mahdollset-tyypilla)
        mahdolliset-nakyvyys-asti (mahdolliset-paatokset-nakyvyys-asti urakan-alkuvuosi mahdolliset-aloitusvuodella)
        mahdolliset-kuluvalle-vuodelle (mahdolliset-paatokset-nakyvyys-vuodella kuluva-hoitovuosi mahdolliset-nakyvyys-asti)
        paatokset (vain-yksi-paatos-per-tyyppi mahdolliset-kuluvalle-vuodelle)]
    paatokset))

(defn yhdista-mapit
  "Yhdistetään tietokannasta tulevat ja päätöskoneelta tulevat päätökset niin, että
   käytetään päätöskoneen päätöksiä, jos niitä ei ole vielä tietokannassa ja muuten tietokannan päätöksiä.
   Vertailussa käytetään :nimi avainta. Se täytyy löytyä molemmista mapeistä."
  [pk-paatokset db-paatokset]
  (let [index-map (into {} (map (fn [m] [(:nimi m) m]) db-paatokset))]
    (map (fn [m1]
           (if-let [m2 (index-map (:nimi m1))]
             m2
             m1))
      pk-paatokset)))

(defn lisaa-paatos-virheellisena
  "Jos päätös on mukana päätöslistassa, mutta sille ei ole antaa tarkentavia tietoja, niin lisätään siihen virhe.
  Mikäli päätöstä ei löydy listasta, niin älä lisää mitään."
  [paatokset nimi virhe lisataan? jarjestys & args]
  (let [virhepaatos (merge (first args)                     ;; Ensimmäinen parametri on päätös
                      {:nimi nimi :virhe virhe :jarjestys jarjestys})]
    (keep identity
         (sort-by :jarjestys
           (if (some #(= (:nimi %) nimi) paatokset)
             (conj
               (filter #(not= (:nimi %) nimi) paatokset)
               ;; Jos ehdot eivät täyttyneet, niin päätöstä ei voida lisätä edes virheellisenä
               (when lisataan?
                 virhepaatos))
             paatokset)))))

(defn laske-indeksikorotus-lupaukselle [db urakkaid paatos-pvm indeksi summa sanktio?]
  (let [indeksikorotus-parametrit {:pvm paatos-pvm
                                   :indeksi indeksi
                                   :maara summa
                                   :urakka-id urakkaid
                                   :sanktiolaji (if sanktio? "lupaussanktio" nil)}
        ;; Taustalla ajetaan tämmönen: SELECT korotus FROM sanktion_indeksikorotus(:pvm::DATE, :indeksi,:maara::NUMERIC, :urakka-id::INTEGER, :sanktiolaji::sanktiolaji);
        indeksikorotus (:korotus (first (lupaus-kyselyt/hae-indeksikorotus-summalle db indeksikorotus-parametrit)))]
    indeksikorotus))

(defn hoitovuosi-paattynyt?
  "Tarkista, onko saatu aika myöhemmin kuin 30.9."
  [valittu-hoitovuosi]
  (let [nyt (pvm/nyt)
        nykyvuosi (pvm/vuosi nyt)
        kuukausi (pvm/kuukausi nyt)
        ;; valittu-hoitovuosi on aina hoitokauden alkuvuosi, mutta hoitokausi päättyy vasta seuraavan vuoden syyskuussa, joten korotetaan yhdellä
        valittu-hoitovuosi (inc valittu-hoitovuosi)]
    (cond
      (> nykyvuosi valittu-hoitovuosi) true
      (and (= nykyvuosi valittu-hoitovuosi) (>= kuukausi 10)) true
      :else false)))

(defn paatos-tallennettu-tietokantaan? [tietokanta-paatokset nimi]
  (:id (first (filter #(when (= (:nimi %) nimi) %) tietokanta-paatokset))))

(defn paatos-mahdollinen? [mahdolliset-paatokset nimi]
  (boolean (seq (filter #(when (= (:nimi %) nimi) %) mahdolliset-paatokset))))

(defn valmistele-lupauspaatokset [db validoinnit-kaytossa? valittu-hoitovuosi urakkaid paatokset toteutuneet-pisteet
                                  luvatut-pisteet tavoitehinta-indeksikorjattu tarjouksen-tavoitehinta indeksi]
  ;; Edeltävät vaatimukset päätöksen tallentamiselle:
  ;; Hoitotovuoden pitää olla päättynyt
  ;; Hoitovuodelle on syötetty tarjouksen tavoitehinta -- Toteutettu
  ;; Hoitovuodelle on syötetty kaikkien lupausten toteumat
  (cond
    ;; Jos validoinnit on asetuksista laitettu päälle, niin hoitovuoden pitää olla päättynyt
    (and validoinnit-kaytossa? (not (hoitovuosi-paattynyt? valittu-hoitovuosi)))
    (lisaa-paatos-virheellisena paatokset "Lupaukset" "Hoitovuosi ei ole päättynyt tai Tarjouksen tavoitehinta -päätös on täyttämättä tai lupausten toteumissa on vielä osa täyttämättä." true 1)
    (and validoinnit-kaytossa? (or (nil? luvatut-pisteet) (nil? toteutuneet-pisteet)))
    (lisaa-paatos-virheellisena paatokset "Lupaukset" "Lupauksia täyttämättä." true 1)
    (and validoinnit-kaytossa? (or (nil? tarjouksen-tavoitehinta) (nil? tavoitehinta-indeksikorjattu)))
    (lisaa-paatos-virheellisena paatokset "Lupaukset" "Tarjouksen tavoitehintaa ei ole määritelty." true 1)
    (and toteutuneet-pisteet luvatut-pisteet tarjouksen-tavoitehinta tavoitehinta-indeksikorjattu)
    (let [;; Urakan parametreista lupaussanktion ja bonuksen prosentit
          urakan-parametrit (first (urakka-kyselyt/hae-urakan-parametrit db {:urakkaid urakkaid}))
          sanktioprosentti (:lupauspaatoksen_sanktioprosentti urakan-parametrit)
          bonusprosentti (:lupauspaatoksen_bonusprosentti urakan-parametrit)
          
          ;; Lasketaan bonus tai sanktio kanonisella domain-funktiolla
          laskenta-tulos (lupaus-domain/laske-lupauspaatos-bonus-tai-sanktio
                          {:toteutuneet-pisteet toteutuneet-pisteet
                           :luvatut-pisteet luvatut-pisteet
                           :tavoitehinta tarjouksen-tavoitehinta
                           :sanktioprosentti sanktioprosentti
                           :bonusprosentti bonusprosentti})]
      ;; Jos laskenta palauttaa nil, bonus- tai sanktioprosentit puuttuvat urakan parametreista
      (if (nil? laskenta-tulos)
        (lisaa-paatos-virheellisena paatokset "Lupaukset" 
                                    "Lupausbonus- tai sanktioprosentit puuttuvat urakan parametreista." 
                                    true 1)
        ;; Muuten jatka normaalisti
        (let [;; Määritä tyyppi laskentatuloksen perusteella
              tyyppi (cond
                       (:lupausbonus laskenta-tulos) "bonus"
                       (:lupaussanktio laskenta-tulos) "sanktio"
                       (:tavoite-taytetty laskenta-tulos) "taytetty"
                       :else "taytetty")
              
              lupausbonus (:lupausbonus laskenta-tulos)
              lupaussanktio (:lupaussanktio laskenta-tulos)
              
              ;; Päätöspäivä on käytössä sanktion laskennassa ja siihen asetetaan hoitovuoden päättymispäivä
              paatospaiva (pvm/->pvm (str "31.10." valittu-hoitovuosi))
              ;; Valitaan lupauspäätös, joissa tyyppi täsmää
              lupauspaatos (first (filter
                                    (fn [paatos]
                                      (and (= (:nimi paatos) "Lupaukset") (= (:tyyppi paatos) tyyppi)))
                                    paatokset))
              indeksikorotus (cond
                               (and (= tyyppi "bonus") (:indeksi_kaytossa_bonuksella urakan-parametrit))
                               (laske-indeksikorotus-lupaukselle db urakkaid paatospaiva indeksi lupausbonus false)

                               (and (= tyyppi "sanktio") (:indeksi_kaytossa_sanktiolla urakan-parametrit))
                               (laske-indeksikorotus-lupaukselle db urakkaid paatospaiva indeksi lupaussanktio true)

                               :else nil)
              ;; Korvataan koneelta saatu päätös tässä valistellulta
              lupauspaatos (-> lupauspaatos
                             (assoc :tyyppi tyyppi)
                             (assoc :lupaussanktio lupaussanktio)
                             (assoc :lupausbonus lupausbonus)
                             (assoc :tavoitehinta tavoitehinta-indeksikorjattu)
                             (assoc :tarjous_tavoitehinta tarjouksen-tavoitehinta)
                             (assoc :luvatut_pisteet luvatut-pisteet)
                             (assoc :toteutuneet_pisteet toteutuneet-pisteet)
                             (assoc :sanktioprosentti sanktioprosentti)
                             (assoc :bonusprosentti bonusprosentti)
                             (assoc :indeksi indeksi)
                             (assoc :indeksikorotus indeksikorotus))
              ;; Poista kaikki lupauspäätökset listasta
              paatokset (remove (fn [paatos] (= (:nimi paatos) "Lupaukset")) paatokset)
              ;; Ja lisää muokattu takaisin
              paatokset (sort-by :jarjestys (conj paatokset lupauspaatos))]
          paatokset)))
    ;; Ehdot eivät täyttyneet, otetaan lupauspäätökset pois listasta ja lisätään virheilmoitus päätökselle
    :else
    (lisaa-paatos-virheellisena paatokset "Lupaukset" "Toteutuneita pisteitä, luvattuja pisteitä tai tarjouksen tavoitehintaa ei ole määritelty." true 1)))

(defn valmistele-tavoitehinnan-muutospaatos [validoinnit-kaytossa? paatokset oikaistu-tavoitehinta kattohinta
                                             muokkaa-kattohinta? kuluva-hoitovuosi]
  ;; Edeltävät vaatimukset päätöksen tallentamiselle:
  ;; - Hoitotovuoden pitää olla päättynyt
  ;; Itse muutoksia (vanhalla kielellä oikaisuja) voi tehdä myös kesken hoitovuoden
  (if-not (first (filter #(when (= (:nimi %) "Tavoitehinnan muutokset") %) paatokset))
    paatokset
    (cond
      ;; Jos validoinnit on asetuksista laitettu päälle, niin hoitovuoden pitää olla päättynyt
      (and validoinnit-kaytossa? (not (hoitovuosi-paattynyt? kuluva-hoitovuosi)))
      (lisaa-paatos-virheellisena paatokset "Tavoitehinnan muutokset" "Hoitovuosi on vielä kesken." true 2)
      (and kattohinta oikaistu-tavoitehinta)
      (let [;; Korvataan koneelta saatu päätös tässä valistellulta
            tavoitehinnan-muutospaatos (first (filter #(when (= (:nimi %) "Tavoitehinnan muutokset") %) paatokset))
            tavoitehinnan-muutospaatos (-> tavoitehinnan-muutospaatos
                                         (assoc :tavoitehinta oikaistu-tavoitehinta)
                                         (assoc :kattohinta kattohinta)
                                         (assoc :muokkaa_kattohinta muokkaa-kattohinta?))
            paatokset (remove (fn [paatos] (= (:nimi paatos) "Tavoitehinnan muutokset")) paatokset)
            paatokset (sort-by :jarjestys (conj paatokset tavoitehinnan-muutospaatos))]
        paatokset)

      :else ;; Ehdot eivät täyttyneet, otetaan lupauspäätökset pois listasta ja lisätään virheilmoitus päätökselle
      (lisaa-paatos-virheellisena paatokset "Tavoitehinnan muutokset" "Tavoitehintaa tai kattohintaa ei ole määritelty." true 2))))


(defn valmistele-tavoitehinnan-pysyva-muutospaatos [validoinnit-kaytossa? paatokset kuluva-hoitovuosi
                                                    kirjallisesti-sovitut-muutokset pysyvat-muutokset muutostyo-muutokset
                                                    jjh-muutokset tehtava-ja-maaramuutos-summa rahavarausmuutos-summa]
  ;; Edeltävät vaatimukset päätöksen tallentamiselle:
  ;; - Hoitotovuoden pitää olla päättynyt

  (if-not (first (filter #(when (= (:nimi %) "Tavoitehinnan pysyvät muutokset") %) paatokset))
    paatokset

    ;; Kokeillaan tähän erilaista lähestymistapaa. Kirjoitetaan validoinnit päätösmäppiin sisälle
    (let [;; Korvataan koneelta saatu päätös tässä valistellulta
          tavoitehinnan-pysyva-muutospaatos (first (filter #(when (= (:nimi %) "Tavoitehinnan pysyvät muutokset") %) paatokset))
          tavoitehinnan-pysyva-muutospaatos (-> tavoitehinnan-pysyva-muutospaatos
                                              (assoc :kirjallisesti-sovitut-muutokset kirjallisesti-sovitut-muutokset)
                                              (assoc :pysyvat-muutokset pysyvat-muutokset)
                                              (assoc :johto-ja-hallintkorvaus-muutokset jjh-muutokset)
                                              (assoc :muutostyo-muutokset muutostyo-muutokset)
                                              (assoc :toteumiin-perustuvat-muutokset (+ tehtava-ja-maaramuutos-summa rahavarausmuutos-summa))
                                              (assoc :tehtava-ja-maaratoteumamuutokset tehtava-ja-maaramuutos-summa)
                                              (assoc :rahavarausten-muutokset rahavarausmuutos-summa)
                                              (assoc :arvonvahennysten-muutokset -1M)
                                              (assoc :tavoitehinnan-muutokset-yhteensa 1M)
                                              (assoc :hoitovuosi-kesken? (and validoinnit-kaytossa? (not (hoitovuosi-paattynyt? kuluva-hoitovuosi)))))
          paatokset (remove (fn [paatos] (= (:nimi paatos) "Tavoitehinnan pysyvät muutokset")) paatokset)
          paatokset (sort-by :jarjestys (conj paatokset tavoitehinnan-pysyva-muutospaatos))]
      paatokset)))

(defn valmistele-indeksikorjauspaatos [validoinnit-kaytossa? paatokset oikaistu-tavoitehinta tavoitehinnan-muutokset
                                       taman-vuoden-muutokset-summa hoitokauden-indeksikuukaudet alkuperainen-pisteluku hoitokauden-alkuvuosi
                                       tietokanta-paatokset tavoitehinta-vahvistettu? urakan-alkuvuosi]
  ;; Edeltävät vaatimukset päätöksen tallentamiselle:
  ;; - Hoitotovuoden pitää olla päättynyt
  ;; - Tavoitehinnan muutokset -päätös on tallennettu

  ;; Mikäli indeksikorjauspäätöstä ei ole päätöslistassa, niin ei lisätä sitä
  (if-not (first (filter #(when (= (:nimi %) "Hoitovuoden lopun indeksikorjaus") %) paatokset))
    paatokset
    (cond
      ;; Jos validoinnit on asetuksista laitettu päälle, tavoitehinta pitää olla vahvistettu
      (and validoinnit-kaytossa? (not tavoitehinta-vahvistettu?))
      (lisaa-paatos-virheellisena paatokset "Hoitovuoden lopun indeksikorjaus" "Hoitovuoden alun indeksikorjattu tavoitehinta on vahvistamatta.
      Voit vahvistaa tiedon hoitovuoden alun tavoitehinta -välilehdeltä." true 3)

      ;; Jos validoinnit on asetuksista laitettu päälle, niin hoitovuoden pitää olla päättynyt
      (and validoinnit-kaytossa? (not (hoitovuosi-paattynyt? hoitokauden-alkuvuosi)))
      (lisaa-paatos-virheellisena paatokset "Hoitovuoden lopun indeksikorjaus" "Hoitovuosi on vielä kesken." true 3)

      ;; Jos validoinnit on asetuksista laitettu päälle, niin Tavoitehinnan muutokset -päätös pitää olla tallennettu
      (and validoinnit-kaytossa? (not (paatos-tallennettu-tietokantaan? tietokanta-paatokset "Tavoitehinnan muutokset")))
      (lisaa-paatos-virheellisena paatokset "Hoitovuoden lopun indeksikorjaus" "Hoitovuoden lopun indeksikorjaus
      laskentaan automaattisesti, kun tavoitehintamuutokset on vahvistettu." true 3)

      (and oikaistu-tavoitehinta hoitokauden-indeksikuukaudet)
      (let [;; Laske pistelukujen muutos
            pisteet (apply + (map #(round2 1 (:indeksiluku %)) hoitokauden-indeksikuukaudet))
            piste-keskiarvo (with-precision 4 (/ pisteet (count hoitokauden-indeksikuukaudet)))
            pistelukujen-muutos (round2 1 (- piste-keskiarvo alkuperainen-pisteluku))
            alkuperaisen-pisteluvun-kuukausi (str "elokuu " hoitokauden-alkuvuosi)
            muutos-prosentteina (round2 1 (* (/ (- piste-keskiarvo alkuperainen-pisteluku) piste-keskiarvo) 100))

            ;; Prosenttiosuus otetaan laskentaan mukaan vain 2% ylittävältä osalta
            indeksikorotuksen-prosenttiosuus (if (> muutos-prosentteina 2) (- muutos-prosentteina 2) 0)
            tavoitehinnan-oikaisut (apply + (map #(or (:summa %) 0) tavoitehinnan-muutokset))
            muutosten-summa (if (>= 2024 urakan-alkuvuosi) tavoitehinnan-oikaisut taman-vuoden-muutokset-summa)
            hv_alun_indkorj_tavoitehinta (- oikaistu-tavoitehinta tavoitehinnan-oikaisut) ;; Meillä on harmillisesti tässä tärkeimmässä tavoitehinta haussa oikaisut mukana
            hoitokauden-lopun-indeksikorjaus (* hv_alun_indkorj_tavoitehinta (/ indeksikorotuksen-prosenttiosuus 100))

            ;; Lisätään mahdolliset puuttuvat kuukaudet UI:n Pistelukujen keskiarvon laskenta listaukseen.
            puuttuvat-kuukaudet (filter #(not (some (fn [kuukausi] (= (:kuukausi kuukausi) (:kuukausi %))) hoitokauden-indeksikuukaudet))
                                  [{:kuukausi (str hoitokauden-alkuvuosi " Lokakuu") :indeksiluku 0}
                                   {:kuukausi (str hoitokauden-alkuvuosi " Marraskuu") :indeksiluku 0}
                                   {:kuukausi (str hoitokauden-alkuvuosi " Joulukuu") :indeksiluku 0}
                                   {:kuukausi (str (+ hoitokauden-alkuvuosi 1) " Tammikuu") :indeksiluku 0}
                                   {:kuukausi (str (+ hoitokauden-alkuvuosi 1) " Helmikuu") :indeksiluku 0}
                                   {:kuukausi (str (+ hoitokauden-alkuvuosi 1) " Maaliskuu") :indeksiluku 0}
                                   {:kuukausi (str (+ hoitokauden-alkuvuosi 1) " Huhtikuu") :indeksiluku 0}
                                   {:kuukausi (str (+ hoitokauden-alkuvuosi 1) " Toukokuu") :indeksiluku 0}
                                   {:kuukausi (str (+ hoitokauden-alkuvuosi 1) " Kesäkuu") :indeksiluku 0}
                                   {:kuukausi (str (+ hoitokauden-alkuvuosi 1) " Heinäkuu") :indeksiluku 0}
                                   {:kuukausi (str (+ hoitokauden-alkuvuosi 1) " Elokuu") :indeksiluku 0}
                                   {:kuukausi (str (+ hoitokauden-alkuvuosi 1) " Syyskuu") :indeksiluku 0}])
            hv_lopun_tavoitehinta_ennen_indkorj (+ oikaistu-tavoitehinta taman-vuoden-muutokset-summa)
            ;; Korvataan koneelta saatu päätös tässä valistellulta
            indeksipaatos (first (filter #(when (= (:nimi %) "Hoitovuoden lopun indeksikorjaus") %) paatokset))
            indeksipaatos (-> indeksipaatos
                            (assoc :hv_alun_indkorj_tavoitehinta hv_alun_indkorj_tavoitehinta) ;; = Hoitovuoden lopun tavoitehinta
                            (assoc :tavoitehinnan_muutokset muutosten-summa)
                            (assoc :hv_lopun_tavoitehinta_ennen_indkorj hv_lopun_tavoitehinta_ennen_indkorj)
                            (assoc :hoitokauden_kuukaudet hoitokauden-indeksikuukaudet)
                            (assoc :puuttuvat_kuukaudet puuttuvat-kuukaudet)
                            (assoc :kuukausien_keskiarvo piste-keskiarvo)
                            (assoc :alkuperainen_pisteluku alkuperainen-pisteluku)
                            (assoc :alkuperaisen_pisteluvun_kuukausi alkuperaisen-pisteluvun-kuukausi)
                            (assoc :pistelukujen_muutos pistelukujen-muutos)
                            (assoc :pistelukujen_muutos_prosentteina muutos-prosentteina)
                            (assoc :indeksikorotuksen_prosenttiosuus indeksikorotuksen-prosenttiosuus)
                            (assoc :hoitokauden_lopun_indeksikorjaus hoitokauden-lopun-indeksikorjaus))

            paatokset (remove (fn [paatos] (= (:nimi paatos) "Hoitovuoden lopun indeksikorjaus")) paatokset)
            paatokset (sort-by :jarjestys (conj paatokset indeksipaatos))]
        paatokset)

      ;; Ehdot eivät täyttyneet, otetaan indeksipäätökset pois listasta ja lisätään virheilmoitus päätökselle
      :else
      (lisaa-paatos-virheellisena paatokset "Hoitovuoden lopun indeksikorjaus" "Tavoitehintaa, tavoitehinnan muutoksia tai hoitokauden indeksikuukausia ei ole määritelty." true 3))))

(defn valmistele-tavoitehinnan-alituspaatos [db validoinnit-kaytossa? urakkaid paatokset urakan-alkuvuosi urakan-loppuvuosi kuluva-hoitovuosi
                                             hoitokauden-alun-tavoitehinta hoitokauden-lopun-indeksikorjattu-tavoitehinta kustannukset
                                             tietokanta-paatokset tavoitehinta-vahvistettu?]
  ;; Edeltävät vaatimukset: Kaikille: Hoitovuoden tulee olla päättynyt
  ;; -24 vuodesta alkaen lisäksi:
  ;; Kustannussuunnitelma vahvistettu
  ;; Tavoitehinnan muutokset tallennettu,
  ;; Hoitovuoden lopun tavoitehintapäätös tallennettu

  ;; Tavoitehinnan alitus päätös täytyy löytyä päätöslistasta. Lisäksi tavoitehinta pitää olla suurempi, kuin kustannukset.
  ;; Muuten ei voida lisätä tavoitehinnan alituspäätöstä ja validoida sitä muuten tarkemmin.
  (if-not (first (filter #(when (= (:nimi %) "Tavoitehinnan alitus") %) paatokset))
    paatokset
    (cond
      (and validoinnit-kaytossa? (not (hoitovuosi-paattynyt? kuluva-hoitovuosi)))
      (lisaa-paatos-virheellisena paatokset "Tavoitehinnan alitus" "Hoitovuosi on vielä kesken." true 5)

      (and validoinnit-kaytossa? (<= 2024 kuluva-hoitovuosi) (not tavoitehinta-vahvistettu?))
      (lisaa-paatos-virheellisena paatokset "Tavoitehinnan alitus" "Kustannussuunnitelma on vahvistamatta." true 5)

      (and validoinnit-kaytossa? (<= 2024 kuluva-hoitovuosi)
        (not (paatos-tallennettu-tietokantaan? tietokanta-paatokset "Tavoitehinnan muutokset")))
      (lisaa-paatos-virheellisena paatokset "Tavoitehinnan alitus" "Tavoitehinnan muutokset -päätös on vielä tekemättä." true 5)

      ;; Vaaditaan hoitovuoden lopun tavoite-ja kattohinta vain jos kuluva hoitovuosi on 2024 tai myöemmin
      ;; Ja, jos urakka on alkanut 2021 tai myöhemmin
      (and validoinnit-kaytossa? (>= urakan-alkuvuosi 2021) (<= 2024 kuluva-hoitovuosi)
        (not (paatos-tallennettu-tietokantaan? tietokanta-paatokset "Hoitovuoden lopun tavoite- ja kattohinta")))
      (lisaa-paatos-virheellisena paatokset "Tavoitehinnan alitus" "Hoitovuoden lopun tavoite- ja kattohinta -päätös on vielä tekemättä." true 5)

      (and hoitokauden-alun-tavoitehinta hoitokauden-lopun-indeksikorjattu-tavoitehinta kustannukset (> hoitokauden-lopun-indeksikorjattu-tavoitehinta kustannukset))
      (let [urakan-parametrit (first (urakka-kyselyt/hae-urakan-parametrit db {:urakkaid urakkaid}))
            tavoitehinnan-alitus (- hoitokauden-lopun-indeksikorjattu-tavoitehinta kustannukset)
            ;; Poistetaan päätöskokneen tavoitehinna alituspäätös ja muokataan se alla
            tavoitehinnan-alituspaatos (first (filter #(when (= (:nimi %) "Tavoitehinnan alitus") %) paatokset))
            paatokset (remove (fn [paatos] (= (:nimi paatos) "Tavoitehinnan alitus")) paatokset)

            ;; (:tavoitepalkkion_maksimi urakan-parametrit) on maksimiprosentti, jota tavoitepalkkiota voidaan maksaa suhteessa hoitokauden alun indeksikorjattuun tavoitehintaan. Yleisimmin 3%
            maksimi-tavoitepalkkio (* (/ (:tavoitepalkkion_maksimi urakan-parametrit) 100) hoitokauden-alun-tavoitehinta)
            tavoitepalkkion-maksuprosentti (:tavoitepalkkion_maksuprosentti urakan-parametrit)
            ;; Tavoitepalkkio on alituksesta max 3% tavoitehinnasta (prosentti tulee parametritaulusta) - Mutta viimeisenä vuotena maksetaan kaikki eli 100% alituksesta
            laskennallinen-tavoitepalkkio (* (/ tavoitepalkkion-maksuprosentti 100) tavoitehinnan-alitus)
            tavoitepalkkio (if (= urakan-loppuvuosi kuluva-hoitovuosi)
                             tavoitehinnan-alitus ;; Viimeisenä vuotena maksetaan kaikki. Muuten 30% tai max 3% , tai versiossa 2 maksetaan 75% alituksesta
                             (min maksimi-tavoitepalkkio laskennallinen-tavoitepalkkio))
            ;; Jos alituksesta maksettava tavoitepalkkio on suurempi, kuin 3% tavoitehinnasta, siirretään ylittävä osuus seuraavan hoitovuden alennukseksi - Paitsi tietenkin viimeisenä vuotena
            siirron-maara (if (= urakan-loppuvuosi kuluva-hoitovuosi)
                            nil ;; Viimeisenä vuotena maksetaan kaikki. Eli ei siirretä mitään
                            (when (> laskennallinen-tavoitepalkkio maksimi-tavoitepalkkio)
                              (- laskennallinen-tavoitepalkkio maksimi-tavoitepalkkio)))
            viimeinen-hoitokausi? (boolean (= kuluva-hoitovuosi urakan-loppuvuosi))
            tavoitehinnan-alituspaatos (-> tavoitehinnan-alituspaatos
                                         (assoc :hoitokauden_alun_tavoitehinta hoitokauden-alun-tavoitehinta)
                                         (assoc :hoitokauden_lopun_tavoitehinta hoitokauden-lopun-indeksikorjattu-tavoitehinta)
                                         (assoc :toteutuneet_kustannukset kustannukset)
                                         (assoc :alituksen_maara tavoitehinnan-alitus)
                                         (assoc :siirron_maara siirron-maara)
                                         (assoc :tavoitepalkkio tavoitepalkkio)
                                         (assoc :tavoitepalkkion_maksuprosentti tavoitepalkkion-maksuprosentti)
                                         (assoc :viimeinen_hoitokausi viimeinen-hoitokausi?)
                                         (assoc :tavoitepalkkion_maksimi_prosentti (:tavoitepalkkion_maksimi urakan-parametrit)))
            paatokset (sort-by :jarjestys (conj paatokset tavoitehinnan-alituspaatos))]
        paatokset)

      :else
      ;; Jos tarvittavia tietoja ei ole, niin poistetaan päätöstyyppi
      (lisaa-paatos-virheellisena paatokset "Tavoitehinnan alitus" "Ei lisätä päätöstä." false 5))))

(defn valmistele-tavoitehinnan-ylityspaatos [db validoinnit-kaytossa? urakkaid paatokset urakan-alkuvuosi
                                             urakan-loppuvuosi kuluva-hoitovuosi hoitovuoden-lopun-tavoitehinta
                                             hoitovuoden-lopun-kattohinta kustannukset tietokanta-paatokset
                                             tavoitehinta-vahvistettu?]
  ;; Edeltävät vaatimukset: Kaikille: Hoitovuoden tulee olla päättynyt
  ;; -24 vuodesta alkaen lisäksi:
  ;; Kustannussuunnitelma vahvistettu
  ;; Tavoitehinnan muutokset tallennettu,
  ;; Hoitovuoden lopun tavoitehintapäätös tallennettu
  (if-not (and hoitovuoden-lopun-kattohinta hoitovuoden-lopun-tavoitehinta kustannukset (> kustannukset hoitovuoden-lopun-tavoitehinta))
    (lisaa-paatos-virheellisena paatokset "Tavoitehinnan ylitys" "Poistetaan vain koko päätös." false 6)

    (cond
      (and validoinnit-kaytossa? (not (hoitovuosi-paattynyt? kuluva-hoitovuosi)))
      (lisaa-paatos-virheellisena paatokset "Tavoitehinnan ylitys" "Hoitovuosi on vielä kesken." true 6)

      (and validoinnit-kaytossa? (<= 2024 kuluva-hoitovuosi) (not tavoitehinta-vahvistettu?))
      (lisaa-paatos-virheellisena paatokset "Tavoitehinnan ylitys" "Kustannussuunnitelma on vahvistamatta." true 6)

      (and validoinnit-kaytossa? (<= 2024 kuluva-hoitovuosi)
        (not (paatos-tallennettu-tietokantaan? tietokanta-paatokset "Tavoitehinnan muutokset")))
      (lisaa-paatos-virheellisena paatokset "Tavoitehinnan ylitys" "Tavoitehinnan muutokset -päätös on vielä tekemättä." true 6)

      ;; Vaaditaan hoitovuoden lopun tavoite-ja kattohinta vain jos kuluva hoitovuosi on 2024 tai myöemmin
      ;; Ja, jos urakka on alkanut 2021 tai myöhemmin
      (and validoinnit-kaytossa? (>= urakan-alkuvuosi 2021) (<= 2024 kuluva-hoitovuosi)
        (not (paatos-tallennettu-tietokantaan? tietokanta-paatokset "Hoitovuoden lopun tavoite- ja kattohinta")))
      (lisaa-paatos-virheellisena paatokset "Tavoitehinnan ylitys" "Hoitovuoden lopun tavoite- ja kattohinta -päätös on vielä tekemättä." true 6)

      (and hoitovuoden-lopun-kattohinta hoitovuoden-lopun-tavoitehinta kustannukset (> kustannukset hoitovuoden-lopun-tavoitehinta))
      (let [urakan-parametrit (first (urakka-kyselyt/hae-urakan-parametrit db {:urakkaid urakkaid}))
            ;; Ylitys + tavoitehinta ei voi ylittää kattohintaa. Eli maksettavat rahat on aina tavoitehinnan ja
            ;; kattohinnan väliin jääviä summia. Kattohinnan ylittävät summat menee aina urakoitsijan maksettavaksi
            tavoitehinnan-ylitys (min (- kustannukset hoitovuoden-lopun-tavoitehinta) (- hoitovuoden-lopun-kattohinta hoitovuoden-lopun-tavoitehinta))
            tavoitehinnan-ylityspaatos (first (filter #(when (= (:nimi %) "Tavoitehinnan ylitys") %) paatokset))
            paatokset (remove (fn [paatos] (= (:nimi paatos) "Tavoitehinnan ylitys")) paatokset)

            ;; Jäljelle jäänyt paatos
            tilaajan-prosentti (:tavoitehinnan_ylityksen_tilaajan_maksuprosentti urakan-parametrit)
            urakoitsijan-prosentti (- 100 tilaajan-prosentti)
            viimeinen-hoitokausi? (boolean (= kuluva-hoitovuosi urakan-loppuvuosi))
            tavoitehinnan-ylityspaatos (-> tavoitehinnan-ylityspaatos
                                         (assoc :urakkaid urakkaid)
                                         (assoc :toteutuneet_kustannukset kustannukset)
                                         (assoc :tavoitehinta hoitovuoden-lopun-tavoitehinta)
                                         (assoc :toteutuneet_kustannukset kustannukset)
                                         (assoc :ylityksen_maara tavoitehinnan-ylitys)
                                         (assoc :tilaajan_prosentti tilaajan-prosentti)
                                         (assoc :urakoitsijan_prosentti urakoitsijan-prosentti)
                                         (assoc :tilaaja_maksaa (* (/ tilaajan-prosentti 100) tavoitehinnan-ylitys))
                                         (assoc :urakoitsija_maksaa (* (/ urakoitsijan-prosentti 100) tavoitehinnan-ylitys))
                                         (assoc :viimeinen_hoitokausi viimeinen-hoitokausi?))
            paatokset (sort-by :jarjestys (conj paatokset tavoitehinnan-ylityspaatos))]
        paatokset)

      :else
      ;; Jos tarvittavia tietoja ei ole, niin poistetaan tavoitehinnan ylitys
      (lisaa-paatos-virheellisena paatokset "Tavoitehinnan ylitys" "Poistetaan ylityspäätös." false 6))))

(defn valmistele-kattohinnan-paatokset [db validoinnit-kaytossa? urakkaid paatokset hoitovuoden-lopun-kattohinta kustannukset
                                        kuluva-hoitovuosi urakan-alkuvuosi urakan-loppuvuosi tietokanta-paatokset tavoitehinta-vahvistettu?]
  ;; Edeltävät vaatimukset: Kaikille: Hoitovuoden tulee olla päättynyt
  ;; -24 vuodesta alkaen lisäksi:
  ;; Kustannussuunnitelma vahvistettu
  ;; Tavoitehinnan muutokset tallennettu,
  ;; Hoitovuoden lopun tavoitehintapäätös tallennettu
  ;; Ja vielä lisäksi: Tavoitehinnan ylityspäätös tallennettu
  (if-not (and hoitovuoden-lopun-kattohinta kustannukset (> kustannukset hoitovuoden-lopun-kattohinta))
    (lisaa-paatos-virheellisena paatokset "Kattohinnan ylitys" "Poistetaan vain koko päätös." false 7)

    (cond
      (and validoinnit-kaytossa? (not (hoitovuosi-paattynyt? kuluva-hoitovuosi)))
      (lisaa-paatos-virheellisena paatokset "Kattohinnan ylitys" "Hoitovuosi on vielä kesken." true 7)

      (and validoinnit-kaytossa? (<= 2024 kuluva-hoitovuosi) (not tavoitehinta-vahvistettu?))
      (lisaa-paatos-virheellisena paatokset "Kattohinnan ylitys" "Kustannussuunnitelma on vahvistamatta." true 7)

      (and validoinnit-kaytossa? (<= 2024 kuluva-hoitovuosi)
        (not (paatos-tallennettu-tietokantaan? tietokanta-paatokset "Tavoitehinnan muutokset")))
      (lisaa-paatos-virheellisena paatokset "Kattohinnan ylitys" "Tavoitehinnan muutokset -päätös on vielä tekemättä." true 7)

      ;; Vaaditaan hoitovuoden lopun tavoite-ja kattohinta vain jos kuluva hoitovuosi on 2024 tai myöemmin
      ;; Ja, jos urakka on alkanut 2021 tai myöhemmin
      (and validoinnit-kaytossa? (>= urakan-alkuvuosi 2021) (<= 2024 kuluva-hoitovuosi)
        (not (paatos-tallennettu-tietokantaan? tietokanta-paatokset "Hoitovuoden lopun tavoite- ja kattohinta")))
      (lisaa-paatos-virheellisena paatokset "Kattohinnan ylitys" "Hoitovuoden lopun tavoite- ja kattohinta -päätös on vielä tekemättä." true 7)

      ;; Vaaditaan Tavoitehinnan ylitys päätös, pitää olla tallennettuna
      ;; Ja, jos urakka on alkanut 2021 tai myöhemmin
      (and validoinnit-kaytossa? (>= urakan-alkuvuosi 2021)
        (not (paatos-tallennettu-tietokantaan? tietokanta-paatokset "Tavoitehinnan ylitys")))
      (lisaa-paatos-virheellisena paatokset "Kattohinnan ylitys" "Tavoitehinnan ylitys -päätös on vielä tekemättä." true 7)

      (and hoitovuoden-lopun-kattohinta kustannukset (> kustannukset hoitovuoden-lopun-kattohinta))
      (let [urakan-parametrit (first (urakka-kyselyt/hae-urakan-parametrit db {:urakkaid urakkaid}))
            kattohinnan-ylityspaatos (first (filter #(= (:nimi %) "Kattohinnan ylitys") paatokset))
            ylityksen-maara (- kustannukset hoitovuoden-lopun-kattohinta)
            viimeinen-hoitokausi? (boolean (= kuluva-hoitovuosi urakan-loppuvuosi))
            siirtorajoitus-prosentti (:kattohintaylityksen_siirron_prosenttirajoitus urakan-parametrit)
            max-siirrettava-maara (if siirtorajoitus-prosentti
                                    (* siirtorajoitus-prosentti hoitovuoden-lopun-kattohinta) ;; Jos rajoitus on käytössä, niin siirretään max annetun prosentin verran)
                                    ylityksen-maara)
            ;; Pyöristetään kahteen desimaaliin, että on vertailtavissa käyttöliittymässä syötettävän määrän kanssa, eikä olematon ero aiheuta validointivirhettä. Käyttöliittymässä summat ovat aina kahdella desimaalilla.
            max-siirrettava-maara (round2 2 (min max-siirrettava-maara ylityksen-maara))
            ;; Täytetään pakolliset tiedot
            kattohinnan-ylityspaatos (-> kattohinnan-ylityspaatos
                                       (assoc :urakkaid urakkaid)
                                       (assoc :toteutuneet_kustannukset kustannukset)
                                       (assoc :kattohinta hoitovuoden-lopun-kattohinta)
                                       (assoc :ylityksen_maara ylityksen-maara)
                                       (assoc :urakoitsija_maksaa ylityksen-maara)
                                       (assoc :siirra? false) ;; Päätöksen pohjatietoja asetettaessa siirto on aina defaulttina false. Tietokannasta haettaessa tilanne voi olla eri.
                                       (assoc :viimeinen_hoitokausi viimeinen-hoitokausi?)
                                       (assoc :maksimi_siirrettava_maara max-siirrettava-maara)
                                       (assoc :siirtorajoitus_prosentti siirtorajoitus-prosentti)
                                       (assoc :siirrettava_maara 0) ;; Aseta defaulttina nollaksi
                                       )

            paatokset (remove
                        (fn [paatos]
                          (= (:nimi paatos) "Kattohinnan ylitys"))
                        paatokset)
            paatokset (sort-by :jarjestys (conj paatokset kattohinnan-ylityspaatos))]
        paatokset)

      ;; Jos tarvittavia tietoja ei ole, niin poistetaan kattohinnan ylitys
      :else
      (lisaa-paatos-virheellisena paatokset "Kattohinnan ylitys" "Kattohintaa tai toteutuneita kustannuksia ei ole määritelty." false 7))))

;; Hoitovuoden lopun tavoite- ja kattohinta
(defn valmistele-hv-lopun-tavoite-ja-kattohinta [validoinnit-kaytossa? urakan-alkuvuosi valittu-hoitovuosi paatokset tavoitehinta-indeksikorjattu
                                                 tavoitehinnan-muutokset taman-vuoden-muutokset-summa hoitokauden-lopun-indeksikorjaus
                                                 hoitovuoden-lopun-kattohinta kattohintakerroin lisaa-hoitokauden-lopun-indeksikorjaus
                                                 tietokanta-paatokset mahdolliset-paatokset]
  ;; Edeltävät vaatimukset päätöksen tallentamiselle:
  ;; Hoitotovuoden pitää olla päättynyt
  ;; Tavoitehinnan muutokset -päätös on tallennettu
  ;; -24/-25 vuosina hoitovuoden lopun indeksikorjaus tulee olla vaihvistettu

  ;; Mikäli Hoitovuoden lopun tavoite- ja kattohinta ei ole päätöslistassa, niin ei lisätä sitä
  (if-not (first (filter #(when (= (:nimi %) "Hoitovuoden lopun tavoite- ja kattohinta") %) paatokset))
    paatokset
    (cond
      (and validoinnit-kaytossa? (not (hoitovuosi-paattynyt? valittu-hoitovuosi)))
      (lisaa-paatos-virheellisena paatokset "Hoitovuoden lopun tavoite- ja kattohinta" "Hoitovuosi on vielä kesken." true 4)

      (and validoinnit-kaytossa? (<= 2024 valittu-hoitovuosi)
        (not (paatos-tallennettu-tietokantaan? tietokanta-paatokset "Tavoitehinnan muutokset")))
      (lisaa-paatos-virheellisena paatokset "Hoitovuoden lopun tavoite- ja kattohinta" "Tavoitehinnan muutokset -päätös on vielä tekemättä." true 4)

      (and validoinnit-kaytossa?
        (and
          (paatos-mahdollinen? mahdolliset-paatokset "Hoitovuoden lopun indeksikorjaus")
          (not (paatos-tallennettu-tietokantaan? tietokanta-paatokset "Hoitovuoden lopun indeksikorjaus"))))
      (lisaa-paatos-virheellisena paatokset "Hoitovuoden lopun tavoite- ja kattohinta" "Hoitovuoden lopun indeksikorjaus -päätös on vielä tekemättä." true 4)

      (and tavoitehinta-indeksikorjattu tavoitehinnan-muutokset hoitovuoden-lopun-kattohinta)
      (let [hintapaatos (first (filter #(= (:nimi %) "Hoitovuoden lopun tavoite- ja kattohinta") paatokset))
            hintamuutos (apply + (map #(or (:summa %) 0) tavoitehinnan-muutokset))
            ;; 2025 vuodesta eteenpäin ei ole käytössä vanhat tavoitehinnan-oikaisut, vaan monimutkaisemmat vuosittaiset muutoset/pysyvät muutokset
            hintamuutos (if (>= 2024 urakan-alkuvuosi) hintamuutos taman-vuoden-muutokset-summa)
            ;; Täytetään pakolliset tiedot
            hintapaatos (-> hintapaatos
                          (assoc :nimi "Hoitovuoden lopun tavoite- ja kattohinta") ;; Nimi löytyy, jos päätösten alkuvuosia ei kovakoodaten vaihdeta testitarkoituksissa
                          (assoc :tavoitehinta_ennen tavoitehinta-indeksikorjattu)
                          (assoc :tavoitehinta_jalkeen (+ tavoitehinta-indeksikorjattu hintamuutos (or hoitokauden-lopun-indeksikorjaus 0)))
                          (assoc :tavoitehinnan_muutokset hintamuutos)
                          (assoc :hoitokauden_lopun_indeksikorjaus (or hoitokauden-lopun-indeksikorjaus 0))
                          (assoc :kattohinta hoitovuoden-lopun-kattohinta)
                          (assoc :kattohintakerroin kattohintakerroin)
                          (assoc :lisaa_tavoitehintaan_lopunindeksikorjaus lisaa-hoitokauden-lopun-indeksikorjaus))

            ;; Siivoa vanha koneelta saatu päätös pois
            paatokset (remove (fn [paatos] (= (:nimi paatos) "Hoitovuoden lopun tavoite- ja kattohinta")) paatokset)
            paatokset (sort-by :jarjestys (conj paatokset hintapaatos))]
        paatokset)

      :else
      ;; Jos tarvittavia tietoja ei ole, niin varoitetaan siitä käyttäjää
      (lisaa-paatos-virheellisena paatokset "Hoitovuoden lopun tavoite- ja kattohinta" "Hoitovuoden lopun indeksikorjaus -päätös on vielä tekemättä." true 4))))

(defn valmistele-hoidonjohtopalkkionmuutospaatos [validoinnit-kaytossa? valittu-hoitovuosi paatokset hv-lopun-tavoitehinta-ilman-indeksia
                                                  tarjouksen-tavoitehinta hoidonjohtopalkkio tietokanta-paatokset urakan-alkuvuosi]
  ;; Edeltävät vaatimukset päätöksen tallentamiselle:
  ;; Hoitotovuoden pitää olla päättynyt
  ;; Hoitovuoden lopun tavoitehinta tulee olla vahvistettu

  ;; Varmistetaan, että tarvittavat tiedot on olemassa
  ;; Varmistetaan möys, että päätös on olemassa

  (if-not (first (filter #(= (:nimi %) "Hoidonjohtopalkkion muutos") paatokset))
    paatokset
    (cond
      (and validoinnit-kaytossa? (not (hoitovuosi-paattynyt? valittu-hoitovuosi)))
      (lisaa-paatos-virheellisena paatokset "Hoidonjohtopalkkion muutos" "Hoitovuosi on vielä kesken." true 9)

      ;; Hoitovuoden lopun tavoite- ja kattohinta -päätös vaaditaan vain, jos urakka on alkanut 2021 tai myöhemmin
      (and validoinnit-kaytossa? (>= urakan-alkuvuosi 2021)
        (not (paatos-tallennettu-tietokantaan? tietokanta-paatokset "Hoitovuoden lopun tavoite- ja kattohinta")))
      (lisaa-paatos-virheellisena paatokset "Hoidonjohtopalkkion muutos" "Hoitovuoden lopun tavoite- ja kattohinta -päätös on vielä tekemättä." true 9)

      (and hv-lopun-tavoitehinta-ilman-indeksia tarjouksen-tavoitehinta hoidonjohtopalkkio)
      (let [paatos (first (filter #(= (:nimi %) "Hoidonjohtopalkkion muutos") paatokset))
            ;; Desimaalien tarkkuus on tärkeää. Käyttöliittymässä kuitenkin käytetään pyöristettyjä lukuja. Taustalla lasketaan raakaluvuilla.
            muutosprosentti-raaka (.divide (bigdec hv-lopun-tavoitehinta-ilman-indeksia) (bigdec tarjouksen-tavoitehinta) 10 BigDecimal/ROUND_HALF_UP)
            ;tulos (with-precision 15 (/ tavoitehinta tarjouksen-tavoitehinta))
            hoidonjohtopalkkio-muutos (if (>= muutosprosentti-raaka 1)
                                        (- (* hoidonjohtopalkkio muutosprosentti-raaka) hoidonjohtopalkkio)
                                        (- (* hoidonjohtopalkkio muutosprosentti-raaka) hoidonjohtopalkkio))
            muutosprosentti (round2 1 (* (- muutosprosentti-raaka 1) 100))
            ;; Hoidonjohtopalkkioon tehdään muutos, jos hoitovuoden lopun tavoitehinta ilman indeksitarkistuksia muuttuu enemmän kuin 5%
            ;; tarjouksen mukaiseen tavoitehintaan verrattuna.
            hoidonjohtopalkkio-muutos (if (or (< muutosprosentti -5) (> muutosprosentti 5))
                                        hoidonjohtopalkkio-muutos
                                        0)

            ;; Täytetään pakolliset tiedot
            paatos (-> paatos
                     (assoc :hv_lopun_indkorjaamaton_tavoitehinta hv-lopun-tavoitehinta-ilman-indeksia)
                     (assoc :tarjouksen_tavoitehinta tarjouksen-tavoitehinta)
                     (assoc :hoidonjohtopalkkio hoidonjohtopalkkio)
                     (assoc :muutosprosentti muutosprosentti)
                     (assoc :hoidonjohtopalkkio_muutos hoidonjohtopalkkio-muutos))

            paatokset (remove
                        (fn [paatos]
                          (= (:nimi paatos) "Hoidonjohtopalkkion muutos"))
                        paatokset)
            paatokset (sort-by :jarjestys (conj paatokset paatos))]
        paatokset)

      :else
      ;; Jos tarvittavia tietoja ei ole, niin varoitetaan siitä käyttäjää
      (let [virhe #{}
            virhe (if-not hv-lopun-tavoitehinta-ilman-indeksia (conj virhe "Hoitovuoden lopun indekiskorjaamatonta tavoitehintaa ei ole määritelty. ") virhe)
            virhe (if-not tarjouksen-tavoitehinta (conj virhe "Tarjouksen tavoitehintaa ei ole määritelty. ") virhe)
            virhe (if-not hoidonjohtopalkkio (conj virhe "Hoidonjohtopalkkiota ei ole määritelty. ") virhe)]
        (lisaa-paatos-virheellisena paatokset "Hoidonjohtopalkkion muutos" (str/join " " virhe) true 9)))))

(defn valmistele-raporttipaatos [validoinnit-kaytossa? valittu-hoitovuosi paatokset]
  ;; Edeltävät vaatimukset päätöksen tallentamiselle:
  ;; Hoitotovuoden pitää olla päättynyt
  (if-not (first (filter #(= (:nimi %) "Välikatselmuspöytäkirjaan liitettävät raportit") paatokset))
    paatokset ;; Raporttipäätöstä ei ole, joten palautetaan päätökset sellaisenaan
    (cond
      (and validoinnit-kaytossa? (not (hoitovuosi-paattynyt? valittu-hoitovuosi)))
      (lisaa-paatos-virheellisena paatokset "Välikatselmuspöytäkirjaan liitettävät raportit" "Hoitovuosi on vielä kesken." true 8)

      ;; Jos hoitovuosi on päättynyt, niin palautetaan päätöslista ja raporttipäätös sellaisenaan
      :else paatokset)))

(defn nimi->avain [nimi]
  (keyword (str/lower-case (-> nimi
                             (str/replace #"ö" "o")
                             (str/replace #"ä" "a")
                             (str/replace #" " "-")
                             (str/replace #"--" "-")))))

(defn filtteroi-mahdolliset-paatokset
  "Poistetaan mahdollisista päätöksistä kaikki päätökset, jotka kuuluvat jo olemassa olevaan luokkaan.
  Esim Lupauspäätöksiä saadaan kolme, mutta niiden järjestysnumero on kaikilla 1, joka
  merkitsee, että ne kuuluvat samaan luokkaan (lupauksiin) ja näin ollen niitä tarvitaan vain yksi."
  [paatokset toteutuneet-kustannukset hoitovuoden-lopun-kattohinta hoitovuoden-lopun-tavoitehinta]
  (let [;; Jos toteuma ei ylitä kattohintaa, niin poistetaan kattohintapäätös
        paatokset (if (or (nil? toteutuneet-kustannukset) (nil? hoitovuoden-lopun-kattohinta) (<= toteutuneet-kustannukset hoitovuoden-lopun-kattohinta))
                    (remove (fn [rivi] (= (:nimi rivi) "Kattohinnan ylitys")) paatokset)
                    paatokset)

        ;; Jos toteuma ei ylitä tavoitehintaan, niin poistetaan tavoihinnan ylityspäätös
        paatokset (if (or (nil? toteutuneet-kustannukset) (nil? hoitovuoden-lopun-tavoitehinta) (<= toteutuneet-kustannukset hoitovuoden-lopun-tavoitehinta))
                    (remove (fn [rivi] (= (:nimi rivi) "Tavoitehinnan ylitys")) paatokset)
                    paatokset)
        ;; Jos toteuma ylittää tavoitehinnan, niin poistetaan tavoihinnan alituspäätös
        paatokset (if (or (nil? toteutuneet-kustannukset) (nil? hoitovuoden-lopun-tavoitehinta) (> toteutuneet-kustannukset hoitovuoden-lopun-tavoitehinta))
                    (remove (fn [rivi] (= (:nimi rivi) "Tavoitehinnan alitus")) paatokset)
                    paatokset)

        ;; Ja jos vielä on päätöksiä, joista on useampi samaa tyyppiä, niin otetaan niistä vain yksi
        paatokset (->> paatokset
                    (group-by :paatostyyppi)
                    (map (fn [[_ paatokset]] (first paatokset)))
                    (into []))]
    paatokset))
