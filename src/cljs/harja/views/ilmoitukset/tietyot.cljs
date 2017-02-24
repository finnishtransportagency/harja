(ns harja.views.ilmoitukset.tietyot
  (:require [reagent.core :refer [atom] :as r]
            [harja.tiedot.ilmoitukset.tietyot :as tiedot]
            [harja.ui.bootstrap :as bs]
            [harja.ui.komponentti :as komp]
            [harja.ui.grid :refer [grid]]
            [harja.ui.yleiset :refer [ajax-loader] :as yleiset]
            [harja.ui.kentat :refer [tee-kentta]]
            [harja.ui.napit :refer [palvelinkutsu-nappi] :as napit]
            [harja.ui.valinnat :refer [urakan-hoitokausi-ja-aikavali]]
            [harja.ui.lomake :as lomake]
            [harja.ui.protokollat :as protokollat]
            [harja.loki :refer [log tarkkaile!]]
            [harja.tiedot.istunto :as istunto]
            [harja.fmt :as fmt]
            [harja.tiedot.navigaatio :as nav]
            [harja.pvm :as pvm]
            [harja.views.kartta :as kartta]
            [harja.ui.ikonit :as ikonit]
            [harja.domain.tierekisteri :as tr-domain]
            [harja.ui.valinnat :as valinnat]
            [harja.ui.notifikaatiot :as notifikaatiot]
            [tuck.core :refer [tuck send-value! send-async!]]
            [harja.ui.kentat :as kentat]
            [harja.domain.oikeudet :as oikeudet]
            [harja.tiedot.kartta :as kartta-tiedot])
  (:require-macros
                   [cljs.core.async.macros :refer [go]]))

(defn aikavalivalitsin [valinnat-nyt]
  (let [alkuaika (:alkuaika valinnat-nyt)
        alkuaikakentta {:nimi :alkuaika
                        :otsikko "Alku"
                        :tyyppi :pvm-aika
                        :validoi [[:ei-tyhja "Anna alkuaika"]]}
        loppuaikakentta {:nimi :loppuaika
                         :otsikko "Loppu"
                         :tyyppi :pvm-aika
                         :validoi [[:ei-tyhja "Anna loppuaika"]
                                   [:pvm-toisen-pvmn-jalkeen alkuaika "Loppuajan on oltava alkuajan jälkeen"]]}]
    (lomake/ryhma
     {:rivi? true}
     alkuaikakentta
     loppuaikakentta)))

(defn ilmoitusten-hakuehdot [e! {:keys [aikavali urakka] :as valinnat-nyt}]
  (log "renskaillaan hakuehtolomake")
  [lomake/lomake
   {:luokka :horizontal
    :muokkaa! #(e! (tiedot/->AsetaValinnat %))}

   [(aikavalivalitsin valinnat-nyt)
    {:nimi :hakuehto :otsikko "Hakusana"
     :placeholder "Hae tekstillä..."
     :tyyppi :string
     :pituus-max 64
     :palstoja 1}
    #_{:nimi :selite
     :palstoja 1
     :otsikko "Selite"
     :placeholder "Hae ja valitse selite"
     :tyyppi :haku
     :hae-kun-yli-n-merkkia 0
     :nayta second :fmt second
     :lahde selitehaku}
    {:nimi :tr-numero
     :palstoja 1
     :otsikko "Tienumero"
     :placeholder "Rajaa tienumerolla"
     :tyyppi :positiivinen-numero :kokonaisluku? true}
    {:nimi :tunniste
     :palstoja 1
     :otsikko "Tunniste"
     :placeholder "Rajaa tunnisteella"
     :tyyppi :string}

    (lomake/ryhma
      {:rivi? true}
      {:nimi :ilmoittaja-nimi
       :palstoja 1
       :otsikko "Ilmoittajan nimi"
       :placeholder "Rajaa ilmoittajan nimellä"
       :tyyppi :string}
      {:nimi :ilmoittaja-puhelin
       :palstoja 1
       :otsikko "Ilmoittajan puhelinnumero"
       :placeholder "Rajaa ilmoittajan puhelinnumerolla"
       :tyyppi :puhelin})

    (lomake/ryhma
      {:rivi? true}
      #_{:nimi :tilat
       :otsikko "Tila"
       :tyyppi :checkbox-group
       :vaihtoehdot tiedot/tila-filtterit
       :vaihtoehto-nayta tilan-selite}
      {:nimi :tyypit
       :otsikko "Tyyppi"
       :tyyppi :checkbox-group
       :vaihtoehdot [:hyva :huono]
       :vaihtoehto-nayta str}
      )]
   valinnat-nyt])

(defn leikkaa-sisalto-pituuteen [pituus sisalto]
  (if (> (count sisalto) pituus)
    (str (fmt/leikkaa-merkkijono pituus sisalto) "...")
    sisalto))

(defn ilmoitusten-paanakyma
  [e! {valinnat-nyt :valinnat
       haetut-ilmoitukset :ilmoitukset
       ilmoituksen-haku-kaynnissa? :ilmoituksen-haku-kaynnissa? :as ilmoitukset}]
  (log "renskaillaan paanakyma")
  [:span.tietyoilmoitukset

   [ilmoitusten-hakuehdot e! valinnat-nyt]
   [:div
    [grid
     {:tyhja (if haetut-ilmoitukset
               "Ei löytyneitä tietoja"
               [ajax-loader "Haetaan ilmoituksia"])
      :rivi-klikattu (when-not ilmoituksen-haku-kaynnissa? #(e! (tiedot/->ValitseIlmoitus %)))
      :piilota-toiminnot true
      :max-rivimaara 500
      :max-rivimaaran-ylitys-viesti "Yli 500 ilmoitusta. Tarkenna hakuehtoja."}

     [
      {:otsikko "Urakka" :nimi :urakkanimi :leveys 7
       :hae (comp fmt/lyhennetty-urakan-nimi :urakkanimi)}
      {:otsikko "Harja-tunniste" :nimi :harja-tunniste :leveys 3}
      {:otsikko "T-LOIK-tunniste" :nimi :t-loik-tunniste :leveys 3}
      {:otsikko "Otsikko" :nimi :otsikko :leveys 7
       :hae #(leikkaa-sisalto-pituuteen 30 (:otsikko %))}
      {:otsikko "Lisätietoja" :nimi :lisatieto :leveys 7
       :hae #(leikkaa-sisalto-pituuteen 30 (:lisatieto %))}
      {:otsikko "Alkaa" :nimi :alkaa
       :hae (comp pvm/pvm-aika :alkaa) :leveys 6}
      {:otsikko "Ilmoitettu" :nimi :ilmoitettu
       :hae (comp pvm/pvm-aika :ilmoitettu) :leveys 6}
      #_{:otsikko "Tyyppi" :nimi :ilmoitustyyppi
         :tyyppi :komponentti
         :komponentti #(ilmoitustyypin-selite (:ilmoitustyyppi %))
         :leveys 2}
      {:otsikko "Sijainti" :nimi :tierekisteri
       :hae #(tr-domain/tierekisteriosoite-tekstina (:tr %))
       :leveys 7}

      #_{:otsikko "Selitteet" :nimi :selitteet
         :tyyppi :komponentti
         :komponentti it/selitelista
         :leveys 6}

      #_{:otsikko "Tila" :nimi :tila :leveys 7 :hae #(tilan-selite (:tila %))}]
     (mapv #(if (:yhteydenottopyynto %)
              (assoc % :lihavoi true)
              %)
           haetut-ilmoitukset)]]])

(defn ilmoituksen-tiedot [e! ilmoitus]
  [:div
   [:span
    [napit/takaisin "Listaa ilmoitukset" #(e! (tiedot/->PoistaIlmoitusValinta))]
    [:div "Tässä ois leikisti ilmoituksen tiedot"]
    #_[it/ilmoitus e! ilmoitus]]])

(defn ilmoitukset* [e! ilmoitukset]
  (log "z 2")
  (e! (tiedot/->YhdistaValinnat @tiedot/valinnat))
  (komp/luo
   (komp/lippu tiedot/karttataso-ilmoitukset)
   (komp/kuuntelija :ilmoitus-klikattu (fn [_ i] (e! (tiedot/->ValitseIlmoitus i))))
   (komp/watcher tiedot/valinnat (fn [_ _ uusi]
                                    (e! (tiedot/->YhdistaValinnat uusi))))
   (komp/sisaan-ulos #(do
                         (notifikaatiot/pyyda-notifikaatiolupa)
                         (reset! nav/kartan-edellinen-koko @nav/kartan-koko)
                         (nav/vaihda-kartan-koko! :M)
                         (kartta-tiedot/kasittele-infopaneelin-linkit!
                           {:ilmoitus {:toiminto (fn [ilmoitus-infopaneelista]
                                                   (e! (tiedot/->ValitseIlmoitus ilmoitus-infopaneelista)))
                                       :teksti "Valitse ilmoitus"}}))
                      #(do
                         (kartta-tiedot/kasittele-infopaneelin-linkit! nil)
                         (nav/vaihda-kartan-koko! @nav/kartan-edellinen-koko)))
   (fn [e! {valittu-ilmoitus :valittu-ilmoitus :as ilmoitukset}]
     [:span
      [kartta/kartan-paikka]
      (if valittu-ilmoitus
        [ilmoituksen-tiedot e! valittu-ilmoitus]
        [ilmoitusten-paanakyma e! ilmoitukset])])))

#_(defn ilmoitukset []
  (log "z 1")
  (fn []
    [tuck tiedot/ilmoitukset ilmoitukset*]
    (log "z 3")
    [:div "terve"]))
