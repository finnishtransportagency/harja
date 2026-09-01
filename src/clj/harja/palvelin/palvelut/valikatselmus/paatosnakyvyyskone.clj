(ns harja.palvelin.palvelut.valikatselmus.paatosnakyvyyskone
  (:require [clojure.string :as str]

            [harja.pvm :as pvm]
            [harja.tyokalut.yleiset :refer [round2]]
            [harja.kyselyt.urakat :as urakka-kyselyt]
            [harja.domain.lupaus-domain :as lupaus-domain]
            [harja.palvelin.palvelut.valikatselmus.apurit :as apurit]))


(defn valmistele-lupauspaatokset [db validoinnit-kaytossa? valittu-hoitovuosi urakkaid paatokset toteutuneet-pisteet
                                  luvatut-pisteet tavoitehinta-indeksikorjattu tarjouksen-tavoitehinta indeksi tietokanta-paatokset
                                  urakan-alkuvuosi]
  ;; Edeltävät vaatimukset päätöksen tallentamiselle:
  ;; Hoitotovuoden pitää olla päättynyt
  ;; Hoitovuodelle on syötetty tarjouksen tavoitehinta
  ;; Hoitovuodelle on syötetty kaikkien lupausten toteumat
  ;; 2024 vuodesta alkaen Hoitovuoden lopun tavoite- ja kattohinta -päätös pitää olla tehtynä ensin
  (let [virheet (cond-> []
                  (and validoinnit-kaytossa? (not (apurit/hoitovuosi-paattynyt? valittu-hoitovuosi)))
                  (conj "Hoitovuosi on kesken.")

                  (nil? luvatut-pisteet)
                  (conj "Luvatut pisteet täyttämättä.")

                  (nil? toteutuneet-pisteet)
                  (conj "Toteutuneet pisteet täyttämättä.")

                  (and validoinnit-kaytossa? (or (nil? tarjouksen-tavoitehinta) (nil? tavoitehinta-indeksikorjattu)))
                  (conj "Tarjouksen tavoitehintaa ei ole määritelty.")

                  ;; Vaaditaan hoitovuoden lopun tavoite-ja kattohinta vain jos kuluva hoitovuosi on 2024 tai myöemmin
                  ;; Ja, jos urakka on alkanut 2021 tai myöhemmin
                  (and validoinnit-kaytossa? (>= urakan-alkuvuosi 2021) (<= 2024 valittu-hoitovuosi)
                    (not (apurit/paatos-tallennettu-tietokantaan? tietokanta-paatokset "Hoitovuoden lopun tavoite- ja kattohinta")))
                  (conj "Hoitovuoden lopun tavoite- ja kattohinta -päätöstä ei ole vahvistettu."))

        ;; Urakan parametreista lupaussanktion ja bonuksen prosentit
        urakan-parametrit (first (urakka-kyselyt/hae-urakan-parametrit db {:urakkaid urakkaid}))
        sanktioprosentti (:lupauspaatoksen_sanktioprosentti urakan-parametrit)
        bonusprosentti (:lupauspaatoksen_bonusprosentti urakan-parametrit)

        ;; Jos laskenta palauttaa nil, bonus- tai sanktioprosentit puuttuvat urakan parametreista
        virheet (if (or (nil? sanktioprosentti) (nil? bonusprosentti))
                  (conj virheet "Lupausbonus- tai sanktioprosentit puuttuvat urakan parametreista.")
                  virheet)

        ;; Lasketaan bonus tai sanktio kanonisella domain-funktiolla
        laskenta-tulos (lupaus-domain/laske-lupauspaatos-bonus-tai-sanktio
                         {:toteutuneet-pisteet toteutuneet-pisteet
                          :luvatut-pisteet luvatut-pisteet
                          :tavoitehinta tarjouksen-tavoitehinta
                          :sanktioprosentti sanktioprosentti
                          :bonusprosentti bonusprosentti})

        ;; Määritä tyyppi laskentatuloksen perusteella - Virhetilanteessa tyypiksi jää "täytetty"
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
                         (apurit/laske-indeksikorotus-lupaukselle db urakkaid paatospaiva indeksi lupausbonus false)

                         (and (= tyyppi "sanktio") (:indeksi_kaytossa_sanktiolla urakan-parametrit))
                         (apurit/laske-indeksikorotus-lupaukselle db urakkaid paatospaiva indeksi lupaussanktio true)

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
                       (assoc :indeksikorotus indeksikorotus)
                       (assoc :hoitovuosi-kesken? (and validoinnit-kaytossa? (not (apurit/hoitovuosi-paattynyt? valittu-hoitovuosi))))
                       (assoc :virheet (when-not (empty? virheet) virheet)))
        ;; Poista kaikki lupauspäätökset listasta
        paatokset (remove (fn [paatos] (= (:nimi paatos) "Lupaukset")) paatokset)
        ;; Ja lisää muokattu takaisin
        paatokset (sort-by :jarjestys (conj paatokset lupauspaatos))]
    paatokset))


(defn valmistele-tavoitehinnan-muutospaatos [validoinnit-kaytossa? paatokset oikaistu-tavoitehinta kattohinta
                                             muokkaa-kattohinta? kuluva-hoitovuosi]
  ;; Edeltävät vaatimukset päätöksen tallentamiselle:
  ;; - Hoitotovuoden pitää olla päättynyt
  ;; Itse muutoksia (vanhalla kielellä oikaisuja) voi tehdä myös kesken hoitovuoden
  (if-not (first (filter #(when (= (:nimi %) "Tavoitehinnan muutokset") %) paatokset))
    paatokset
    (let [virheet (cond-> []
                    (and validoinnit-kaytossa? (not (apurit/hoitovuosi-paattynyt? kuluva-hoitovuosi)))
                    (conj "Hoitovuosi on kesken.")

                    (not oikaistu-tavoitehinta)
                    (conj "Tavoitehinta puuttuu.")

                    (not kattohinta)
                    (conj "Kattohinta puuttuu."))

          ;; Korvataan koneelta saatu päätös tässä valistellulta
          tavoitehinnan-muutospaatos (first (filter #(when (= (:nimi %) "Tavoitehinnan muutokset") %) paatokset))
          tavoitehinnan-muutospaatos (-> tavoitehinnan-muutospaatos
                                       (assoc :tavoitehinta oikaistu-tavoitehinta)
                                       (assoc :kattohinta kattohinta)
                                       (assoc :muokkaa_kattohinta muokkaa-kattohinta?)
                                       (assoc :hoitovuosi-kesken? (not (apurit/hoitovuosi-paattynyt? kuluva-hoitovuosi)))
                                       (assoc :virheet (when-not (empty? virheet) virheet)))
          paatokset (remove (fn [paatos] (= (:nimi paatos) "Tavoitehinnan muutokset")) paatokset)
          paatokset (sort-by :jarjestys (conj paatokset tavoitehinnan-muutospaatos))]
      paatokset)))


(defn valmistele-tavoitehinnan-pysyva-muutospaatos [validoinnit-kaytossa? paatokset kuluva-hoitovuosi
                                                    kirjallisesti-sovitut-muutokset pysyvat-muutokset muutostyo-muutokset
                                                    jjh-muutokset tehtava-ja-maaramuutos-summa rahavarausmuutos-summa
                                                    arvonvahennykset-yht]
  ;; Edeltävät vaatimukset päätöksen tallentamiselle:
  ;; - Hoitotovuoden pitää olla päättynyt

  (if-not (first (filter #(when (= (:nimi %) "Tavoitehinnan pysyvät muutokset") %) paatokset))
    paatokset

    ;; Kokeillaan tähän erilaista lähestymistapaa. Kirjoitetaan validoinnit päätösmäppiin sisälle
    (let [virheet (cond-> []
                    (and validoinnit-kaytossa? (not (apurit/hoitovuosi-paattynyt? kuluva-hoitovuosi)))
                    (conj "Hoitovuosi on kesken."))

          tavoitehinna-muutokset-yhteensa (+ (or kirjallisesti-sovitut-muutokset 0) (or pysyvat-muutokset 0) (or muutostyo-muutokset 0)
                                            (or jjh-muutokset 0) (or tehtava-ja-maaramuutos-summa 0) (or rahavarausmuutos-summa 0)
                                            (or arvonvahennykset-yht 0))
          ;; Korvataan koneelta saatu päätös tässä valistellulta
          tavoitehinnan-pysyva-muutospaatos (first (filter #(when (= (:nimi %) "Tavoitehinnan pysyvät muutokset") %) paatokset))
          tavoitehinnan-pysyva-muutospaatos (-> tavoitehinnan-pysyva-muutospaatos
                                              (assoc :kirjallisesti_sovitut_muutokset (or kirjallisesti-sovitut-muutokset 0))
                                              (assoc :pysyvat_muutokset (or pysyvat-muutokset 0))
                                              (assoc :johto_ja_hallintakorvaus_muutokset (or jjh-muutokset 0))
                                              (assoc :muutostyo_muutokset (or muutostyo-muutokset 0))
                                              (assoc :toteumiin_perustuvat_muutokset (+ (or tehtava-ja-maaramuutos-summa 0) (or rahavarausmuutos-summa 0)))
                                              (assoc :tehtava_ja_maaratoteumamuutokset (or tehtava-ja-maaramuutos-summa 0))
                                              (assoc :rahavarausten_muutokset (or rahavarausmuutos-summa 0))
                                              (assoc :arvonvahennysten_muutokset (or arvonvahennykset-yht 0))
                                              (assoc :tavoitehinnan_muutokset_yhteensa (or tavoitehinna-muutokset-yhteensa 0))
                                              (assoc :hoitovuosi-kesken? (and validoinnit-kaytossa? (not (apurit/hoitovuosi-paattynyt? kuluva-hoitovuosi))))
                                              (assoc :virheet (when-not (empty? virheet) virheet)))
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
      (apurit/lisaa-paatos-virheellisena paatokset "Hoitovuoden lopun indeksikorjaus" "Hoitovuoden alun indeksikorjattu tavoitehinta on vahvistamatta.
      Voit vahvistaa tiedon hoitovuoden alun tavoitehinta -välilehdeltä." true 3)

      ;; Jos validoinnit on asetuksista laitettu päälle, niin hoitovuoden pitää olla päättynyt
      (and validoinnit-kaytossa? (not (apurit/hoitovuosi-paattynyt? hoitokauden-alkuvuosi)))
      (apurit/lisaa-paatos-virheellisena paatokset "Hoitovuoden lopun indeksikorjaus" "Hoitovuosi on vielä kesken." true 3)

      ;; Jos validoinnit on asetuksista laitettu päälle, niin Tavoitehinnan muutokset -päätös pitää olla tallennettu
      (and validoinnit-kaytossa? (not (apurit/paatos-tallennettu-tietokantaan? tietokanta-paatokset "Tavoitehinnan muutokset")))
      (apurit/lisaa-paatos-virheellisena paatokset "Hoitovuoden lopun indeksikorjaus" "Hoitovuoden lopun indeksikorjaus
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
      (apurit/lisaa-paatos-virheellisena paatokset "Hoitovuoden lopun indeksikorjaus" "Tavoitehintaa, tavoitehinnan muutoksia tai hoitokauden indeksikuukausia ei ole määritelty." true 3))))


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
    (let [virheet (cond-> []
                    (and validoinnit-kaytossa? (<= 2024 kuluva-hoitovuosi)
                      (not (apurit/paatos-tallennettu-tietokantaan? tietokanta-paatokset "Tavoitehinnan muutokset")))
                    (conj "Tavoitehinnan muutokset -päätös on vielä tekemättä.")
                    (and validoinnit-kaytossa? (<= 2024 kuluva-hoitovuosi) (not tavoitehinta-vahvistettu?))
                    (conj "Kustannussuunnitelma on vahvistamatta.")
                    (and validoinnit-kaytossa? (>= urakan-alkuvuosi 2021) (<= 2024 kuluva-hoitovuosi)
                      (not (apurit/paatos-tallennettu-tietokantaan? tietokanta-paatokset "Hoitovuoden lopun tavoite- ja kattohinta")))
                    (conj "Hoitovuoden lopun tavoite- ja kattohinta -päätös on vielä tekemättä.")

                    (and validoinnit-kaytossa? (not (apurit/hoitovuosi-paattynyt? kuluva-hoitovuosi)))
                    (conj "Hoitovuosi on kesken."))
          urakan-parametrit (first (urakka-kyselyt/hae-urakan-parametrit db {:urakkaid urakkaid}))
          tavoitehinnan-alitus (if (and hoitokauden-lopun-indeksikorjattu-tavoitehinta kustannukset)
                                 (- hoitokauden-lopun-indeksikorjattu-tavoitehinta kustannukset)
                                 0)
          ;; Poistetaan päätöskokneen tavoitehinna alituspäätös ja muokataan se alla
          tavoitehinnan-alituspaatos (first (filter #(when (= (:nimi %) "Tavoitehinnan alitus") %) paatokset))
          paatokset (remove (fn [paatos] (= (:nimi paatos) "Tavoitehinnan alitus")) paatokset)

          ;; (:tavoitepalkkion_maksimi urakan-parametrit) on maksimiprosentti, jota tavoitepalkkiota voidaan maksaa suhteessa hoitokauden alun indeksikorjattuun tavoitehintaan. Yleisimmin 3%
          maksimi-tavoitepalkkio (if hoitokauden-alun-tavoitehinta
                                   (* (/ (:tavoitepalkkion_maksimi urakan-parametrit) 100) hoitokauden-alun-tavoitehinta)
                                   0)
          tavoitepalkkion-maksuprosentti (:tavoitepalkkion_maksuprosentti urakan-parametrit)
          ;; Tavoitepalkkio on alituksesta max 3% tavoitehinnasta (prosentti tulee parametritaulusta) - Mutta viimeisenä vuotena maksetaan kaikki eli 100% alituksesta
          maksuprosentti (/ tavoitepalkkion-maksuprosentti 100)
          laskennallinen-tavoitepalkkio (when tavoitehinnan-alitus (* maksuprosentti tavoitehinnan-alitus))
          ;; Viimeisenä vuotena maksetaan maksuprosentin mukaan. Eli esim 30% alituksesta. (-25 alkavilla 75%)
          ;; Muina vuosina siirrettäväksi summaksi ja tavoitepalkkioksi tulee maksimimaksuprosentin mukainen summa
          viimeinen-hoitokausi? (boolean (= kuluva-hoitovuosi urakan-loppuvuosi))
          tavoitepalkkio (if viimeinen-hoitokausi?
                           laskennallinen-tavoitepalkkio
                           (min maksimi-tavoitepalkkio laskennallinen-tavoitepalkkio))
          ;; Jos alituksesta maksettava tavoitepalkkio on suurempi, kuin 3% tavoitehinnasta, siirretään ylittävä osuus seuraavan hoitovuden alennukseksi - Paitsi tietenkin viimeisenä vuotena
          siirron-maara (if (= urakan-loppuvuosi kuluva-hoitovuosi)
                          nil ;; Viimeisenä vuotena maksetaan kaikki. Eli ei siirretä mitään
                          (when (> laskennallinen-tavoitepalkkio maksimi-tavoitepalkkio)
                            (- laskennallinen-tavoitepalkkio maksimi-tavoitepalkkio)))

          tavoitehinnan-alituspaatos (-> tavoitehinnan-alituspaatos
                                       (assoc :hoitokauden_alun_tavoitehinta hoitokauden-alun-tavoitehinta)
                                       (assoc :hoitokauden_lopun_tavoitehinta hoitokauden-lopun-indeksikorjattu-tavoitehinta)
                                       (assoc :toteutuneet_kustannukset kustannukset)
                                       (assoc :alituksen_maara tavoitehinnan-alitus)
                                       (assoc :siirron_maara siirron-maara)
                                       (assoc :tavoitepalkkio tavoitepalkkio)
                                       (assoc :tavoitepalkkion_maksuprosentti tavoitepalkkion-maksuprosentti)
                                       (assoc :viimeinen_hoitokausi viimeinen-hoitokausi?)
                                       (assoc :tavoitepalkkion_maksimi_prosentti (:tavoitepalkkion_maksimi urakan-parametrit))
                                       (assoc :virheet (when-not (empty? virheet) virheet)))
          paatokset (sort-by :jarjestys (conj paatokset tavoitehinnan-alituspaatos))]
      paatokset)))


(defn valmistele-tavoitehinnan-ylityspaatos [_db validoinnit-kaytossa? urakkaid paatokset urakan-alkuvuosi
                                             urakan-loppuvuosi kuluva-hoitovuosi hoitovuoden-lopun-tavoitehinta
                                             hoitovuoden-lopun-kattohinta kustannukset tietokanta-paatokset
                                             tavoitehinta-vahvistettu? urakan-parametrit]
  ;; Edeltävät vaatimukset: Kaikille: Hoitovuoden tulee olla päättynyt
  ;; -24 vuodesta alkaen lisäksi:
  ;; Kustannussuunnitelma vahvistettu
  ;; Tavoitehinnan muutokset tallennettu,
  ;; Hoitovuoden lopun tavoitehintapäätös tallennettu
  (let [virheet (cond-> []
                  (and validoinnit-kaytossa? (<= 2024 kuluva-hoitovuosi) (not tavoitehinta-vahvistettu?))
                  (conj "Kustannussuunnitelma on vahvistamatta.")
                  (and validoinnit-kaytossa? (<= 2024 kuluva-hoitovuosi)
                    (not (apurit/paatos-tallennettu-tietokantaan? tietokanta-paatokset "Tavoitehinnan muutokset")))
                  (conj "Tavoitehinnan muutokset -päätös on vielä tekemättä.")

                  (and validoinnit-kaytossa? (>= urakan-alkuvuosi 2021) (<= 2024 kuluva-hoitovuosi)
                    (not (apurit/paatos-tallennettu-tietokantaan? tietokanta-paatokset "Hoitovuoden lopun tavoite- ja kattohinta")))
                  (conj "Hoitovuoden lopun tavoite- ja kattohinta -päätös on vielä tekemättä.")

                  (and validoinnit-kaytossa? (not (apurit/hoitovuosi-paattynyt? kuluva-hoitovuosi)))
                  (conj "Hoitovuosi on kesken."))

        ;; Ylitys + tavoitehinta ei voi ylittää kattohintaa. Eli maksettavat rahat on aina tavoitehinnan ja
        ;; kattohinnan väliin jääviä summia. Kattohinnan ylittävät summat menee aina urakoitsijan maksettavaksi
        tavoitehinnan-ylitys (min
                               (- (or kustannukset 0) (or hoitovuoden-lopun-tavoitehinta 0))
                               (- (or hoitovuoden-lopun-kattohinta 0) (or hoitovuoden-lopun-tavoitehinta 0)))
        tavoitehinnan-ylityspaatos (first (filter #(when (= (:nimi %) "Tavoitehinnan ylitys") %) paatokset))
        paatokset (remove (fn [paatos] (= (:nimi paatos) "Tavoitehinnan ylitys")) paatokset)

        ;; Jäljelle jäänyt paatos
        tilaajan-prosentti (:tavoitehinnan_ylityksen_tilaajan_maksuprosentti urakan-parametrit)
        urakoitsijan-prosentti (- 100 tilaajan-prosentti)
        viimeinen-hoitokausi? (boolean (= kuluva-hoitovuosi urakan-loppuvuosi))
        tavoitehinnan-ylityspaatos (-> tavoitehinnan-ylityspaatos
                                     (assoc :urakkaid urakkaid)
                                     (assoc :toteutuneet_kustannukset kustannukset)
                                     (assoc :tavoitehinta (or hoitovuoden-lopun-tavoitehinta 0))
                                     (assoc :toteutuneet_kustannukset (or kustannukset 0))
                                     (assoc :ylityksen_maara tavoitehinnan-ylitys)
                                     (assoc :tilaajan_prosentti tilaajan-prosentti)
                                     (assoc :urakoitsijan_prosentti urakoitsijan-prosentti)
                                     (assoc :tilaaja_maksaa (* (/ tilaajan-prosentti 100) (or tavoitehinnan-ylitys 0)))
                                     (assoc :urakoitsija_maksaa (* (/ urakoitsijan-prosentti 100) (or tavoitehinnan-ylitys 0)))
                                     (assoc :viimeinen_hoitokausi viimeinen-hoitokausi?)
                                     (assoc :virheet (when-not (empty? virheet) virheet)))
        ;; Lisätään muokattu päätös takaisin listaan vain jos tavoitehinnan ylitys on suurempi kuin 0. Muuten päätös poistetaan listasta
        paatokset (if (> tavoitehinnan-ylitys 0)
                    (sort-by :jarjestys (conj paatokset tavoitehinnan-ylityspaatos))
                    paatokset)]
    paatokset))


(defn valmistele-kattohinnan-paatokset [db validoinnit-kaytossa? urakkaid paatokset hoitovuoden-lopun-kattohinta kustannukset
                                        kuluva-hoitovuosi urakan-alkuvuosi urakan-loppuvuosi tietokanta-paatokset tavoitehinta-vahvistettu?]
  ;; Edeltävät vaatimukset: Kaikille: Hoitovuoden tulee olla päättynyt
  ;; -24 vuodesta alkaen lisäksi:
  ;; Kustannussuunnitelma vahvistettu
  ;; Tavoitehinnan muutokset tallennettu,
  ;; Hoitovuoden lopun tavoitehintapäätös tallennettu
  ;; Ja vielä lisäksi: Tavoitehinnan ylityspäätös tallennettu
  (if-not (and hoitovuoden-lopun-kattohinta kustannukset (> kustannukset hoitovuoden-lopun-kattohinta))
    (apurit/lisaa-paatos-virheellisena paatokset "Kattohinnan ylitys" "Poistetaan vain koko päätös." false 7)

    (cond
      (and validoinnit-kaytossa? (<= 2024 kuluva-hoitovuosi) (not tavoitehinta-vahvistettu?))
      (apurit/lisaa-paatos-virheellisena paatokset "Kattohinnan ylitys" "Kustannussuunnitelma on vahvistamatta." true 7)

      (and validoinnit-kaytossa? (<= 2024 kuluva-hoitovuosi)
        (not (apurit/paatos-tallennettu-tietokantaan? tietokanta-paatokset "Tavoitehinnan muutokset")))
      (apurit/lisaa-paatos-virheellisena paatokset "Kattohinnan ylitys" "Tavoitehinnan muutokset -päätös on vielä tekemättä." true 7)

      ;; Vaaditaan hoitovuoden lopun tavoite-ja kattohinta vain jos kuluva hoitovuosi on 2024 tai myöemmin
      ;; Ja, jos urakka on alkanut 2021 tai myöhemmin
      (and validoinnit-kaytossa? (>= urakan-alkuvuosi 2021) (<= 2024 kuluva-hoitovuosi)
        (not (apurit/paatos-tallennettu-tietokantaan? tietokanta-paatokset "Hoitovuoden lopun tavoite- ja kattohinta")))
      (apurit/lisaa-paatos-virheellisena paatokset "Kattohinnan ylitys" "Hoitovuoden lopun tavoite- ja kattohinta -päätös on vielä tekemättä." true 7)

      ;; Vaaditaan Tavoitehinnan ylitys päätös, pitää olla tallennettuna
      ;; Ja, jos urakka on alkanut 2021 tai myöhemmin
      (and validoinnit-kaytossa? (>= urakan-alkuvuosi 2021)
        (not (apurit/paatos-tallennettu-tietokantaan? tietokanta-paatokset "Tavoitehinnan ylitys")))
      (apurit/lisaa-paatos-virheellisena paatokset "Kattohinnan ylitys" "Tavoitehinnan ylitys -päätös on vielä tekemättä." true 7)

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
      (apurit/lisaa-paatos-virheellisena paatokset "Kattohinnan ylitys" "Kattohintaa tai toteutuneita kustannuksia ei ole määritelty." false 7))))


;; Hoitovuoden lopun tavoite- ja kattohinta
(defn valmistele-hv-lopun-tavoite-ja-kattohinta [validoinnit-kaytossa? urakan-alkuvuosi valittu-hoitovuosi paatokset tavoitehinta-indeksikorjattu
                                                 tavoitehinnan-muutokset taman-vuoden-muutokset-summa hoitokauden-lopun-indeksikorjaus
                                                 hoitovuoden-lopun-kattohinta kattohintakerroin lisaa-hoitokauden-lopun-indeksikorjaus
                                                 tietokanta-paatokset mahdolliset-paatokset tavoitehinta-vahvistettu?]
  ;; Edeltävät vaatimukset päätöksen tallentamiselle:
  ;; Hoitotovuoden pitää olla päättynyt
  ;; Tavoitehinnan muutokset -päätös on tallennettu
  ;; -24/-25 vuosina hoitovuoden lopun indeksikorjaus tulee olla vaihvistettu

  ;; Mikäli Hoitovuoden lopun tavoite- ja kattohinta ei ole päätöslistassa, niin ei lisätä sitä
  (if-not (first (filter #(when (= (:nimi %) "Hoitovuoden lopun tavoite- ja kattohinta") %) paatokset))
    paatokset
    (let [virheet (cond-> [] (and validoinnit-kaytossa? (<= 2024 valittu-hoitovuosi)
                               (not (apurit/paatos-tallennettu-tietokantaan? tietokanta-paatokset "Tavoitehinnan muutokset")))
                    (conj "Tavoitehinnan muutokset -päätös on vielä tekemättä.")
                    (and validoinnit-kaytossa?
                      (and
                        (apurit/paatos-mahdollinen? mahdolliset-paatokset "Hoitovuoden lopun indeksikorjaus")
                        (not (apurit/paatos-tallennettu-tietokantaan? tietokanta-paatokset "Hoitovuoden lopun indeksikorjaus"))))
                    (conj "Hoitovuoden lopun indeksikorjaus -päätös on vielä tekemättä.")

                    (not tavoitehinta-indeksikorjattu)
                    (conj "Tavoitehinta puuttuu.")

                    (not hoitovuoden-lopun-kattohinta)
                    (conj "Kattohinta puuttuu.")

                    (and validoinnit-kaytossa? (<= 2024 valittu-hoitovuosi) (not tavoitehinta-vahvistettu?))
                    (conj "Kustannussuunnitelma on vahvistamatta.")

                    (and validoinnit-kaytossa? (not (apurit/hoitovuosi-paattynyt? valittu-hoitovuosi)))
                    (conj "Hoitovuosi on kesken."))

          hintapaatos (first (filter #(= (:nimi %) "Hoitovuoden lopun tavoite- ja kattohinta") paatokset))
          hintamuutos (if tavoitehinnan-muutokset (apply + (map #(or (:summa %) 0) tavoitehinnan-muutokset)) 0)
          ;; 2025 vuodesta eteenpäin ei ole käytössä vanhat tavoitehinnan-oikaisut, vaan monimutkaisemmat vuosittaiset muutoset/pysyvät muutokset
          hintamuutos (if (>= 2024 urakan-alkuvuosi) hintamuutos taman-vuoden-muutokset-summa)
          ;; Täytetään pakolliset tiedot
          hintapaatos (-> hintapaatos
                        (assoc :nimi "Hoitovuoden lopun tavoite- ja kattohinta") ;; Nimi löytyy, jos päätösten alkuvuosia ei kovakoodaten vaihdeta testitarkoituksissa
                        (assoc :tavoitehinta_ennen tavoitehinta-indeksikorjattu)
                        (assoc :tavoitehinta_jalkeen (+ (or tavoitehinta-indeksikorjattu 0) (or hintamuutos 0) (or hoitokauden-lopun-indeksikorjaus 0)))
                        (assoc :tavoitehinnan_muutokset hintamuutos)
                        (assoc :hoitokauden_lopun_indeksikorjaus (or hoitokauden-lopun-indeksikorjaus 0))
                        (assoc :kattohinta hoitovuoden-lopun-kattohinta)
                        (assoc :kattohintakerroin kattohintakerroin)
                        (assoc :lisaa_tavoitehintaan_lopunindeksikorjaus lisaa-hoitokauden-lopun-indeksikorjaus)
                        (assoc :virheet (when-not (empty? virheet) virheet)))

          ;; Siivoa vanha koneelta saatu päätös pois
          paatokset (remove (fn [paatos] (= (:nimi paatos) "Hoitovuoden lopun tavoite- ja kattohinta")) paatokset)
          paatokset (sort-by :jarjestys (conj paatokset hintapaatos))]
      paatokset)))


(defn valmistele-hoidonjohtopalkkionmuutospaatos [validoinnit-kaytossa? valittu-hoitovuosi paatokset hv-lopun-tavoitehinta-ilman-indeksia
                                                  tarjouksen-tavoitehinta hoidonjohtopalkkio tietokanta-paatokset urakan-alkuvuosi]
  ;; Edeltävät vaatimukset päätöksen tallentamiselle:
  ;; Hoitotovuoden pitää olla päättynyt
  ;; Hoitovuoden lopun tavoitehinta tulee olla vahvistettu

  ;; Varmistetaan, että tarvittavat tiedot on olemassa
  ;; Varmistetaan möys, että päätös on olemassa

  (if-not (first (filter #(= (:nimi %) "Hoidonjohtopalkkion muutos") paatokset))
    paatokset
    (let [virheet (cond-> []
                    (and validoinnit-kaytossa? (not (apurit/hoitovuosi-paattynyt? valittu-hoitovuosi)))
                    (conj "Hoitovuosi on kesken.")

                    (and validoinnit-kaytossa? (>= urakan-alkuvuosi 2021)
                      (not (apurit/paatos-tallennettu-tietokantaan? tietokanta-paatokset "Hoitovuoden lopun tavoite- ja kattohinta")))
                    (conj "Hoitovuoden lopun tavoite- ja kattohinta -päätös on vielä tekemättä.")

                    (not hv-lopun-tavoitehinta-ilman-indeksia)
                    (conj "Hoitovuoden lopun tavoite puuttuu.")

                    (not tarjouksen-tavoitehinta)
                    (conj "Tarjouksen tavoitehinta puuttuu.")

                    (not hoidonjohtopalkkio)
                    (conj "Hoidonjohtopalkkio puuttuu."))

          paatos (first (filter #(= (:nimi %) "Hoidonjohtopalkkion muutos") paatokset))
          ;; Desimaalien tarkkuus on tärkeää. Käyttöliittymässä kuitenkin käytetään pyöristettyjä lukuja. Taustalla lasketaan raakaluvuilla.
          muutosprosentti-raaka (if (and hv-lopun-tavoitehinta-ilman-indeksia tarjouksen-tavoitehinta)
                                  (.divide (bigdec hv-lopun-tavoitehinta-ilman-indeksia) (bigdec tarjouksen-tavoitehinta) 10 BigDecimal/ROUND_HALF_UP)
                                  0)
          hoidonjohtopalkkio (if hoidonjohtopalkkio hoidonjohtopalkkio 0)
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
                   (assoc :hoidonjohtopalkkio_muutos hoidonjohtopalkkio-muutos)
                   (assoc :hoitovuosi-kesken? (and validoinnit-kaytossa? (not (apurit/hoitovuosi-paattynyt? valittu-hoitovuosi))))
                   (assoc :virheet (when-not (empty? virheet) virheet)))

          paatokset (remove
                      (fn [paatos]
                        (= (:nimi paatos) "Hoidonjohtopalkkion muutos"))
                      paatokset)
          paatokset (sort-by :jarjestys (conj paatokset paatos))]
      paatokset)))


(defn valmistele-raporttipaatos [validoinnit-kaytossa? valittu-hoitovuosi paatokset]
  (if (first (filter #(= (:nimi %) "Välikatselmuspöytäkirjaan liitettävät raportit") paatokset))
    ;; Mikäli raporttipäätös on olemassa
    (let [paatos (first (filter #(= (:nimi %) "Välikatselmuspöytäkirjaan liitettävät raportit") paatokset))
          paatos (if (and validoinnit-kaytossa? (not (apurit/hoitovuosi-paattynyt? valittu-hoitovuosi)))
                   (assoc paatos :virhe "Hoitovuosi on kesken.")
                   paatos)
          paatokset (remove #(= (:nimi %) "Välikatselmuspöytäkirjaan liitettävät raportit") paatokset)]
      (sort-by :jarjestys (conj paatokset paatos)))
    ;; Muuten palautetaan vain alkuperäinen lista
    paatokset))

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
