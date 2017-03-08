(ns harja.views.ilmoitukset.tietyoilmoitukset
  (:require [reagent.core :refer [atom] :as r]
            [clojure.string :as s]
            [harja.tiedot.ilmoitukset.tietyoilmoitukset :as tiedot]
            [harja.ui.bootstrap :as bs]
            [harja.ui.komponentti :as komp]
            [harja.ui.grid :refer [grid]]
            [harja.ui.yleiset :refer [ajax-loader] :as yleiset]
            [harja.ui.kentat :refer [tee-kentta]]
            [harja.ui.napit :refer [palvelinkutsu-nappi] :as napit]
            [harja.ui.valinnat :refer [urakan-hoitokausi-ja-aikavali]]
            [harja.ui.lomake :as lomake]
            [harja.ui.protokollat :as protokollat]
            [harja.ui.debug :as ui-debug]
            [harja.loki :refer [tarkkaile! log]]
            [cljs.pprint :refer [pprint]]
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
            [harja.tiedot.hallintayksikot :as hallintayksikot-tiedot]
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

(defn ilmoitusten-hakuehdot [e! valinnat-nyt]
  (log "---> renskaillaan hakuehdot. valinnat nyt: " (pr-str (:alkuaika valinnat-nyt))
       (pr-str (:loppuaika valinnat-nyt))) ;; nil nil toisella kerralla, ekalla ok
  [lomake/lomake
   {:luokka :horizontal
    :muokkaa! #(e! (tiedot/->AsetaValinnat %))}

   [(aikavalivalitsin valinnat-nyt)]
   valinnat-nyt])

(defn ilmoitusten-paanakyma
  [e! {valinnat-nyt :valinnat
       haetut-ilmoitukset :ilmoitukset
       ilmoituksen-haku-kaynnissa? :ilmoituksen-haku-kaynnissa? :as ilmoitukset}]
  (log "renskaillaan paanakyma, valinnat" (pr-str valinnat-nyt))
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
      {:otsikko "Urakka" :nimi :urakka_nimi :leveys 5
       :hae (comp fmt/lyhennetty-urakan-nimi :urakka_nimi)}
      {:otsikko "Tie" :nimi :tie
       :hae #(str (:tr_numero % "(ei tien numeroa)") " " (:tien_nimi % "(ei tien nimeä)"))
       :leveys 4}
      {:otsikko "Alkupvm" :nimi :alku
       :hae (comp pvm/pvm-aika :alku)

       :leveys 2}
      {:otsikko "Loppupvm" :nimi :loppu
       :hae (comp pvm/pvm-aika :loppu) :leveys 2}
      {:otsikko "Työn tyyppi" :nimi :tyotyypit
       :hae #(s/join ", " (->> % :tyotyypit (map :tyyppi)))
       :leveys 4}
      {:otsikko "Ilmoittaja" :nimi :ilmoittaja
       :hae #(str (:ilmoittaja_etunimi %) " " (:ilmoittaja_sukunimi %))
       :leveys 7}]
     haetut-ilmoitukset]]])

(def koskee-valinnat [[nil "Valitse..."]
                      [:ensimmainen "Ensimmäinen ilmoitus työstä"],
                      [:muutos "Korjaus/muutos aiempaan tietoon"],
                      [:tyovaihe "Työvaihetta koskeva ilmoitus"],
                      [:paattyminen "Työn päättymisilmoitus"]])


(defn- projekti-valinnat [urakat]
  (partition 2
             (interleave
              (mapv (comp str :id) urakat) (mapv :nimi urakat))))

(defn- pvm-vali-paivina [p1 p2]

  (let [pp1 (pvm/->pvm p1)
        pp2 (pvm/->pvm p2)]
    (log "pvm-vali-paivina kutsuttu" p1 p2 pp1 pp2)
    (when (and pp1 pp2)
      (/ (Math/abs (- p1 p2)) (* 1000 60 60 24)))))

(defn ilmoituksen-tiedot [e! ilmoitus kayttajan-urakat]
  (fn [e! ilmoitus]
    [:div
     [:span
      [napit/takaisin "Takaisin ilmoitusluetteloon" #(e! (tiedot/->PoistaIlmoitusValinta))]
      [lomake/lomake {:otsikko "Muokkaa ilmoitusta"
                      :muokkaa #(e! tiedot/->IlmoitustaMuokattu %)}
       [(lomake/ryhma
         "Ilmoitus koskee"
         {:nimi :koskee
          :otsikko "Ilmoitus koskee:"
          :tyyppi :valinta
          :valinnat koskee-valinnat
          :valinta-nayta second
          :valinta-arvo first
          :muokattava? (constantly true)}
         )
        (lomake/ryhma "Tiedot koko kohteesta"
         {:nimi :projekti-tai-urakka
          :otsikko "Projekti tai urakka:"
          :tyyppi :valinta
          :valinnat (projekti-valinnat kayttajan-urakat)
          :valinta-nayta second :valinta-arvo first
          :muokattava? (constantly true)
          }
         {:nimi :urakoitsijan-nimi
          :otsikko "Urakoitsijan nimi"
          :muokattava? (constantly true)
          :tyyppi :string
          }
         {:nimi :urakoitsijan-yhteyshenkilo
          :otsikko "Urakoitsijan yhteyshenkilö"
          :hae #(str (:urakoitsijayhteyshenkilo_etunimi %) " " (:urakoitsijayhteyshenkilo_sukunimi %))
          :muokattava? (constantly true)
          :tyyppi :string
          }
         {:nimi :urakoitsijayhteyshenkilo_matkapuhelin
          :otsikko "Puhelinnumero"
          :tyyppi :string
          }
         {:nimi :tilaajan-nimi
          :otsikko "Tilaajan nimi"
          :muokattava? (constantly true)
          :tyyppi :string
          }
        {:nimi :tilaajan-yhteyshenkilo
         :otsikko "Tilaajan yhteyshenkilö"
         :hae #(str (:tilaajayhteyshenkilo_etunimi %) " " (:tilaajayhteyshenkilo_sukunimi %))
         :muokattava? (constantly true)
         :tyyppi :string
         }
        {:nimi :tilaajayhteyshenkilo_matkapuhelin
         :otsikko "Puhelinnumero"
         :tyyppi :string
         }
         {:nimi :tr_numero
          :otsikko "Tienumero"
          :tyyppi :positiivinen-numero
          :muokattava? (constantly true)
          }
         {:otsikko "Tierekisteriosoite"
          :nimi :tr-osoite
          :pakollinen? true
          :tyyppi :tierekisteriosoite
          :ala-nayta-virhetta-komponentissa? true
          :validoi [[:validi-tr "Reittiä ei saada tehtyä" [:sijainti]]]
          :sijainti (r/wrap (:sijainti ilmoituksen-tiedot)
                            #(log "laitettas sijainti" (pr-str %)))
          }


         {:otsikko "Tien nimi" :nimi :tien_nimi
          :tyyppi :string
          }
         {:otsikko "Kunta/kunnat" :nimi :kunnat
          :tyyppi :string
          }

         {:otsikko "Työn alkupiste (osoite, paikannimi)" :nimi :alkusijainnin_kuvaus
          :tyyppi :string
          }
         {:otsikko "Työn aloituspvm" :nimi :alku :tyyppi :pvm}
         {:otsikko "Työn loppupiste (osoite, paikannimi)" :nimi :loppusijainnin_kuvaus
          :tyyppi :string
          }
         {:otsikko "Työn lopetuspvm" :nimi :loppu :tyyppi :pvm}

         {:otsikko "Työn pituus" :nimi :tyon-pituus
          :tyyppi :positiivinen-numero
          :hae #(pvm-vali-paivina (:alku %) (:loppu %))}

         )

        (lomake/ryhma "Työvaihe"
                      )
        (lomake/ryhma "Työn tyyppi"
                      )
        (lomake/ryhma "Työaika"
                      )
        (lomake/ryhma "Vaikutukset liikenteelle"
                      )
        (lomake/ryhma "Vaikutussuunta"
                      )
        (lomake/ryhma "Muuta"
                      )]
       ilmoitus]
      #_[:div
       [bs/panel {}
        [yleiset/tietoja {}
         "Loppusijainti:" (:loppusijainnin_kuvaus ilmoitus)
         "Urakka:" (:urakka_nimi ilmoitus)
         "Lisätietoja:" (when (:lisatietoja ilmoitus)
                          [yleiset/pitka-teksti (:lisatieto ilmoitus)])
         ]
        ]]

      ]]))

(defn ilmoitukset* [e! ilmoitukset]
  (e! (tiedot/->HaeKayttajanUrakat @hallintayksikot-tiedot/hallintayksikot))
  (e! (tiedot/->YhdistaValinnat @tiedot/ulkoisetvalinnat))
  (komp/luo
   (komp/lippu tiedot/karttataso-ilmoitukset)
   (komp/kuuntelija :ilmoitus-klikattu (fn [_ i] (e! (tiedot/->ValitseIlmoitus i))))
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
   (fn [e! {valittu-ilmoitus :valittu-ilmoitus kayttajan-urakat :kayttajan-urakat :as app}]
     [:span
      #_[ui-debug/debug {:ilmoitukset @tiedot/ilmoitukset
                       ;;:tiedot/ulkoisetvalinnat @tiedot/ulkoisetvalinnat
                       }]
      [ui-debug/debug  @tiedot/ilmoitukset]
      [kartta/kartan-paikka]
      (if valittu-ilmoitus
        [ilmoituksen-tiedot e! valittu-ilmoitus kayttajan-urakat]
        [ilmoitusten-paanakyma e! app])])))
