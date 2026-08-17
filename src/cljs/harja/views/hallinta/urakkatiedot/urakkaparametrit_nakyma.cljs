(ns harja.views.hallinta.urakkatiedot.urakkaparametrit_nakyma
  "Urakoilla on urakka_parametrit-tietokantataulussa erilaisia urakan tyyppiin ja alkuvuoteen perustuvia parametreja
  (indeksit, sanktiot, bonukset jne). Tämä näkymä näyttää valitun urakan parametrit vain lukutilassa,
  parametrien muokkaus ei kuulu tähän näkymään."
  (:require [tuck.core :refer [tuck]]
            [harja.pvm :as pvm]
            [harja.ui.komponentti :as komp]
            [harja.ui.yleiset :as yleiset :refer [ajax-loader-pieni]]
            [harja.tiedot.hallinta.urakkatiedot.urakkaparametrit-tiedot :as tiedot]))

(def ^:private parametrien-ryhmat
  "Parametrien ryhmittely osioittain näyttöjärjestyksessä."
  [{:nimi "Indeksit"
    :parametrit [:indeksi_kaytossa_sanktiolla :indeksi_kaytossa_bonuksella]}
   {:nimi "Bonukset"
    :parametrit [:lupauspaatoksen_bonusprosentti]}
   {:nimi "Sanktiot"
    :parametrit [:lupauspaatoksen_sanktioprosentti]}
   {:nimi "Välikatselmus"
    :parametrit [:lisaa_tavoitehintaan_hoitovuodenlopunindeksikorjaus
                 :hoitokauden_lopun_kattohinta_kerroin
                 :muokkaa_kattohinta_kasin
                 :kattohintaylityksen_siirron_prosenttirajoitus
                 :tavoitepalkkion_maksuprosentti
                 :tavoitepalkkion_maksimi
                 :tavoitehinnan_ylityksen_urakoitsijan_maksuprosentti
                 :tavoitehinnan_ylityksen_tilaajan_maksuprosentti]}
   {:nimi "Sampo-integraatio"
    :parametrit [:maksuera_lahetys_sampo
                 :kustannussuunnitelma_lahetys_sampo]}
   {:nimi "Muutokset"
    :parametrit [:muutosten_hallinta]}
   {:nimi "Laskutusraja"
    :parametrit [:laskutusraja_kaytossa]}
   {:nimi "Parametrien muokkaustiedot"
    :parametrit [:luotu :muokattu]}])

(def ^:private parametrien-otsikot
  "Urakka_parametrit-taulun sarakkeet ja niiden suomenkieliset otsikot."
  {:indeksi_kaytossa_sanktiolla "Indeksi käytössä sanktiolla"
   :indeksi_kaytossa_bonuksella "Indeksi käytössä bonuksella"
   :lupauspaatoksen_bonusprosentti "Lupauspäätöksen bonusprosentti"
   :lupauspaatoksen_sanktioprosentti "Lupauspäätöksen sanktioprosentti"
   :lisaa_tavoitehintaan_hoitovuodenlopunindeksikorjaus "Lisätään tavoitehintaan hoitovuoden lopun indeksikorjaus"
   :hoitokauden_lopun_kattohinta_kerroin "Hoitokauden lopun kattohinnan kerroin"
   :muokkaa_kattohinta_kasin "Kattohinta annetaan käsin"
   :kattohintaylityksen_siirron_prosenttirajoitus "Kattohintaylityksen siirron prosenttirajoitus"
   :tavoitehinnan_ylityksen_urakoitsijan_maksuprosentti "Tavoitehinnan ylityksen urakoitsijan maksuprosentti"
   :tavoitehinnan_ylityksen_tilaajan_maksuprosentti "Tavoitehinnan ylityksen tilaajan maksuprosentti"
   :tavoitepalkkion_maksuprosentti "Tavoitepalkkion maksuprosentti"
   :tavoitepalkkion_maksimi "Tavoitepalkkion maksimi"
   :maksuera_lahetys_sampo "Maksuerätietojen lähetys Sampoon"
   :kustannussuunnitelma_lahetys_sampo "Kustannussuunnitelmatietojen lähetys Sampoon"
   :laskutusraja_kaytossa "Laskutusraja käytössä"
   :muutosten_hallinta "Muutosten hallinta käytössä"
   :luotu "Luotu"
   :muokattu "Muokattu"})

(defn- arvon-teksti
  "Muuntaa arvon näyttökelpoiseen merkkijonomuotoon.
   Päivämäärät (DateTime, LocalDate) formatoidaan pvm/pvm-aika funktioilla."
  [arvo]
  (cond
    (nil? arvo) "-"
    (true? arvo) "Kyllä"
    (false? arvo) "Ei"
    ;; Tarkista onko päivämäärä (DateTime = pvm+aika, LocalDate = pelkkä pvm)
    (pvm/pvm? arvo) (pvm/pvm arvo)
    ;; Muut arvot merkkijonoksi
    :else (str arvo)))

(defn- parametrit-lista [parametrit-data]
  [:div.urakan-parametrit-lista
   (doall
     (for [{:keys [nimi parametrit]} parametrien-ryhmat]
       ^{:key nimi}
       [:div.urakan-parametrit-ryhma
        [:h3.urakan-parametrit-osio nimi]
        [:dl
         (for [avain parametrit]
           ^{:key avain}
           [:div.urakan-parametrit-rivi
            [:dt (get parametrien-otsikot avain)]
            [:dd (arvon-teksti (get parametrit-data avain))]])]]))])

(defn nakyma* [e! _app]
  (komp/luo
    (komp/sisaan #(e! (tiedot/->HaeUrakat)))
    (fn [e! {:keys [urakat valittu-urakka parametrit urakat-haku-kaynnissa? parametrit-haku-kaynnissa?]}]
      [:div.urakan-parametrit
       [:h1 "Urakan parametrit"]
       [:p "Valitse urakka, jonka urakka_parametrit-taulun tiedot haluat nähdä. Tiedot ovat vain luku -tilassa."]
       [:div.urakan-parametrit-valitsin
        [yleiset/pudotusvalikko "Urakka"
         {:valinta valittu-urakka
          :format-fn :nimi
          :vayla-tyyli? true
          :naytettava-arvo (when-not valittu-urakka "Valitse urakka...")
          :valitse-fn #(e! (tiedot/->ValitseUrakka %))}
         urakat]]
       (cond
         urakat-haku-kaynnissa?
         [ajax-loader-pieni "Haetaan urakoita..."]

         parametrit-haku-kaynnissa?
         [ajax-loader-pieni "Haetaan urakan parametreja..."]

         (and valittu-urakka (nil? parametrit))
         [:p "Urakalle ei ole määritelty parametreja."]

         parametrit
         [parametrit-lista parametrit])])))

(defn urakkaparametrit []
  [tuck tiedot/tila nakyma*])
