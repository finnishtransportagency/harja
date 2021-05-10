(ns harja.views.urakka.pot2.pot2-lomake
"POT2-lomake"
  (:require
    [reagent.core :refer [atom] :as r]
    [harja.domain.oikeudet :as oikeudet]
    [harja.domain.paallystysilmoitus :as pot]
    [harja.domain.pot2 :as pot2-domain]
    [harja.domain.tierekisteri :as tr]
    [harja.domain.yllapitokohde :as yllapitokohteet-domain]
    [harja.loki :refer [log]]
    [harja.ui.debug :refer [debug]]
    [harja.ui.grid :as grid]
    [harja.ui.komponentti :as komp]
    [harja.ui.lomake :as lomake]
    [harja.ui.napit :as napit]
    [harja.ui.ikonit :as ikonit]
    [harja.ui.yleiset :refer [ajax-loader] :as yleiset]
    [harja.tiedot.navigaatio :as nav]
    [harja.tiedot.urakka.paallystys :as paallystys]
    [harja.tiedot.urakka.pot2.materiaalikirjasto :as mk-tiedot]
    [harja.tiedot.urakka.pot2.pot2-tiedot :as pot2-tiedot]
    [harja.views.urakka.pot2.alusta :as alusta]
    [harja.views.urakka.pot2.paallystekerros :as paallystekerros]
    [harja.views.urakka.pot-yhteinen :as pot-yhteinen]
    [harja.ui.kentat :as kentat]
    [harja.views.urakka.pot2.murskeet :as murskeet]
    [harja.views.urakka.pot2.massat :as massat]
    [harja.ui.varmista-kayttajalta :as varmista-kayttajalta]
    [harja.asiakas.kommunikaatio :as k]
    [harja.domain.paallystys-ja-paikkaus :as paallystys-ja-paikkaus])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]
                   [harja.atom :refer [reaction<!]]))

(defn- otsikkotiedot [{:keys [tila] :as perustiedot}]
  [:span
   [:h1 (str "Päällystysilmoitus - "
                   (pot-yhteinen/paallystyskohteen-fmt perustiedot))]
   [:div
    [:div.inline-block.pot-tila {:class (when tila (name tila))}
     (paallystys-ja-paikkaus/kuvaile-ilmoituksen-tila tila)]]])

(defn tallenna
  [e! {:keys [tekninen-osa tila]}
   {:keys [kayttaja urakka-id valmis-tallennettavaksi?]}]
  (let [paatos-tekninen-osa (:paatos tekninen-osa)
        huomautusteksti
        (cond (and (not= :lukittu tila)
                   (= :hyvaksytty paatos-tekninen-osa))
              "Päällystysilmoitus hyväksytty, ilmoitus lukitaan tallennuksen yhteydessä."
              :default nil)]

    [:div.pot-tallennus
     (when huomautusteksti
       (lomake/yleinen-huomautus huomautusteksti))

     [napit/palvelinkutsu-nappi
      "Tallenna"
      ;; Palvelinkutsunappi olettaa saavansa kanavan. Siksi go.
      #(go
         (e! (pot2-tiedot/->TallennaPot2Tiedot)))
      {:luokka "nappi-ensisijainen"
       :data-cy "pot-tallenna"
       :id "tallenna-paallystysilmoitus"
       :disabled (or (false? valmis-tallennettavaksi?)
                     (not (oikeudet/voi-kirjoittaa?
                            oikeudet/urakat-kohdeluettelo-paallystysilmoitukset
                            urakka-id kayttaja)))
       :ikoni (ikonit/tallenna)
       :virheviesti "Tallentaminen epäonnistui"}]]))

(def pot2-validoinnit
  {:perustiedot paallystys/perustietojen-validointi
   :paallystekerros {:rivi [{:fn paallystekerros/validoi-paallystekerros
                             :sarakkeet {:tr-numero :tr-numero
                                         :tr-ajorata :tr-ajorata
                                         :tr-kaista :tr-kaista
                                         :tr-alkuosa :tr-alkuosa
                                         :tr-alkuetaisyys :tr-alkuetaisyys
                                         :tr-loppuosa :tr-loppuosa
                                         :tr-loppuetaisyys :tr-loppuetaisyys}}]
                     :taulukko [{:fn (r/partial paallystekerros/kohde-toisten-kanssa-paallekkain-validointi true)
                                 :sarakkeet {:tr-numero :tr-numero
                                             :tr-ajorata :tr-ajorata
                                             :tr-kaista :tr-kaista
                                             :tr-alkuosa :tr-alkuosa
                                             :tr-alkuetaisyys :tr-alkuetaisyys
                                             :tr-loppuosa :tr-loppuosa
                                             :tr-loppuetaisyys :tr-loppuetaisyys}}]}
   :alusta {:rivi [{:fn alusta/alustan-validointi
                    :sarakkeet {:tr-numero :tr-numero
                                :tr-ajorata :tr-ajorata
                                :tr-kaista :tr-kaista
                                :tr-alkuosa :tr-alkuosa
                                :tr-alkuetaisyys :tr-alkuetaisyys
                                :tr-loppuosa :tr-loppuosa
                                :tr-loppuetaisyys :tr-loppuetaisyys}}]
            :taulukko [{:fn #(println "todo") ;Mietitään myöhemmin, otetaanko taulukkovalidointia tässä käyttöön. POT1:ssä se aiheutti valtavaa hitautta, backend validointi ehkä riittää niihin ja hyvä vikaviestin raportointi
                        :sarakkeet {:tr-numero :tr-numero
                                    :tr-ajorata :tr-ajorata
                                    :tr-kaista :tr-kaista
                                    :tr-alkuosa :tr-alkuosa
                                    :tr-alkuetaisyys :tr-alkuetaisyys
                                    :tr-loppuosa :tr-loppuosa
                                    :tr-loppuetaisyys :tr-loppuetaisyys}}]}})

(def materiaalikirjasto-tyhja-txt
  "Päällystysilmoitusta on uudistettu. Urakoissa käytetyt massat ja murkseet syötetään ensin materiaalikirjastoon.
  Sen jälkeen niitä voi lisätä kulutuskerroksen ja alustan riveille. Aloita siis menemällä materiaalikirjastoon ao. painikkeesta, kiitos.")

(def materiaalikirjasto-napin-tooltip
  "Urakan materiaalikirjastoon syötetään urakan päällystystöissä käytetyt massat ja murskeet.")

(defn avaa-materiaalikirjasto-nappi [toiminto tyyli]
  [yleiset/wrap-if true
   [yleiset/tooltip {} :% materiaalikirjasto-napin-tooltip]
   [napit/nappi "Muokkaa urakan materiaaleja"
   toiminto
   {:ikoni (ikonit/livicon-pen)
    :luokka "nappi-toissijainen"
    :style (merge {:margin-left "0"}
                  tyyli)}]])

(defn- toimenpiteet-ja-materiaalit-otsikkorivi
  "Toimenpiteiden ja materiaalien otsikkorivi, jossa joitakin toimintoja"
  [e! massat murskeet]
  (let [materiaalikirjasto-tyhja? (and (empty? massat)
                                       (empty? murskeet))]
    [:div
     (when materiaalikirjasto-tyhja?
       [:div {:style {:margin-top "24px"
                      :margin-bottom "24px"}}
        [yleiset/vihje materiaalikirjasto-tyhja-txt]])
     [avaa-materiaalikirjasto-nappi #(e! (mk-tiedot/->NaytaModal true))]]))

(defn lisatiedot
  [e! lisatiedot-atom]
  [:span
   [:h6 "Lisätiedot ja huomautukset"]
   [kentat/tee-kentta {:tyyppi :text :nimi :lisatiedot :koko [80 4]}
    (r/wrap @lisatiedot-atom #(do
                                (e! (pot2-tiedot/->Pot2Muokattu))
                                (reset! lisatiedot-atom %)))]])

(defn pot2-lomake
  [e! {paallystysilmoitus-lomakedata :paallystysilmoitus-lomakedata
       :as              app}
   lukko urakka kayttaja]
  ;; Toistaiseksi ei käytetä lukkoa POT2-näkymässä
  (let [muokkaa! (fn [f & args]
                   (e! (pot2-tiedot/->PaivitaTila [:paallystysilmoitus-lomakedata] (fn [vanha-arvo]
                                                                                     (apply f vanha-arvo args)))))
        perustiedot-hash-avatessa (hash (lomake/ilman-lomaketietoja (:perustiedot paallystysilmoitus-lomakedata)))
        {:keys [tr-numero tr-alkuosa tr-loppuosa]} (get-in paallystysilmoitus-lomakedata [:perustiedot :tr-osoite])]
    (komp/luo
      (komp/lippu pot2-tiedot/pot2-nakymassa?)
      (komp/sisaan (fn [this]
                     (e! (paallystys/->HaeTrOsienPituudet tr-numero tr-alkuosa tr-loppuosa))
                     (e! (paallystys/->HaeTrOsienTiedot tr-numero tr-alkuosa tr-loppuosa))
                     (reset! pot2-tiedot/kohdeosat-atom
                             (-> (:paallystekerros paallystysilmoitus-lomakedata)
                                 (pot2-domain/lisaa-paallystekerroksen-jarjestysnro 1)
                                 (yllapitokohteet-domain/jarjesta-yllapitokohteet)
                                 (yllapitokohteet-domain/indeksoi-kohdeosat)))
                     (reset! pot2-tiedot/alustarivit-atom
                             (-> (:alusta paallystysilmoitus-lomakedata)
                                 (yllapitokohteet-domain/jarjesta-yllapitokohteet)
                                 (yllapitokohteet-domain/indeksoi-kohdeosat)))
                     (reset! pot2-tiedot/lisatiedot-atom (:lisatiedot paallystysilmoitus-lomakedata))
                     (nav/vaihda-kartan-koko! :S)))
      (fn [e! {:keys [paallystysilmoitus-lomakedata massat murskeet materiaalikoodistot
                      pot2-massa-lomake pot2-murske-lomake] :as app}]
        (let [perustiedot (:perustiedot paallystysilmoitus-lomakedata)
              perustiedot-app (select-keys paallystysilmoitus-lomakedata #{:perustiedot :kirjoitusoikeus? :ohjauskahvat})
              massalomake-app (select-keys app #{:pot2-massa-lomake :materiaalikoodistot})
              murskelomake-app (select-keys app #{:pot2-murske-lomake :materiaalikoodistot})
              alusta-app (select-keys paallystysilmoitus-lomakedata #{:kirjoitusoikeus? :perustiedot :alusta :alustalomake})
              paallystekerros-app (select-keys paallystysilmoitus-lomakedata #{:kirjoitusoikeus? :perustiedot :paallystekerros})
              tallenna-app (select-keys (get-in app [:paallystysilmoitus-lomakedata :perustiedot])
                                        #{:tekninen-osa :tila})
              {:keys [tila]} perustiedot
              huomautukset (paallystys/perustietojen-huomautukset (:tekninen-osa perustiedot-app)
                                                                  (:valmispvm-kohde perustiedot-app))
              virheet (conj []
                            (-> perustiedot ::lomake/virheet))
              valmis-tallennettavaksi? (and
                                         (not= tila :lukittu)
                                         (empty? (flatten (keep vals virheet))))
              perustiedot-hash-rendatessa (hash (lomake/ilman-lomaketietoja (:perustiedot paallystysilmoitus-lomakedata)))
              tietoja-muokattu? (or
                                  (not= perustiedot-hash-avatessa perustiedot-hash-rendatessa)
                                  (:muokattu? paallystysilmoitus-lomakedata))]
          [:div.pot2-lomake
           [napit/takaisin
            "Takaisin ilmoitusluetteloon"
            #(if tietoja-muokattu?
               (varmista-kayttajalta/varmista-kayttajalta
                 {:otsikko "Lomakkeelta poistuminen"
                  :sisalto (str "Lomakkeella on tallentamattomia tietoja. Jos poistut, menetät tekemäsi muutokset. Haluatko varmasti poistua lomakkeelta?")
                  :hyvaksy "Poistu tallentamatta"
                  :peruuta-txt "Palaa lomakkeelle"
                  :toiminto-fn (fn []
                                 (e! (pot2-tiedot/->MuutaTila [:paallystysilmoitus-lomakedata] nil)))})
               (e! (pot2-tiedot/->MuutaTila [:paallystysilmoitus-lomakedata] nil)))]
           (when-not (k/kehitysymparistossa?)
             [:p {:style {:color "red"}}
              "Tämä on kehitysversio uudesta päällystysilmoituksesta, joka tulee käyttöön kauden 2021 päällystyksiin. Ethän vielä tee tällä lomakkeella kirjauksia tuotannossa, kiitos."])
           [otsikkotiedot perustiedot]
           (when (= :lukittu tila)
             [pot-yhteinen/poista-lukitus e! urakka])
           [:hr]
           [pot-yhteinen/paallystysilmoitus-perustiedot
            e! perustiedot-app urakka false muokkaa! pot2-validoinnit huomautukset]
           [:hr]
           [toimenpiteet-ja-materiaalit-otsikkorivi e! massat murskeet]
           [yleiset/valitys-vertical]
           [paallystekerros/paallystekerros e! paallystekerros-app {:massat massat
                                                                    :materiaalikoodistot materiaalikoodistot
                                                                    :validointi (:paallystekerros pot2-validoinnit)} pot2-tiedot/kohdeosat-atom]
           [yleiset/valitys-vertical]
           [alusta/alusta e! alusta-app {:massat massat :murskeet murskeet
                                         :materiaalikoodistot materiaalikoodistot
                                         :validointi (:alusta pot2-validoinnit)}
            pot2-tiedot/alustarivit-atom]
           ;; jos käyttäjä haluaa katsella sivupaneelissa massan tai murskeen tietoja
           (cond (and pot2-massa-lomake (:sivulle? pot2-massa-lomake))
                 [massat/massa-lomake e! massalomake-app]

                 (and pot2-murske-lomake (:sivulle? pot2-murske-lomake))
                 [murskeet/murske-lomake e! murskelomake-app ]

                 :else
                 [:span])
           [yleiset/valitys-vertical]
           [lisatiedot e! pot2-tiedot/lisatiedot-atom]
           [yleiset/valitys-vertical]
           (when (= :lukittu (get perustiedot :tila))
             [:div {:style {:margin-bottom "16px"}}
              "Päällystysilmoitus lukittu, tietoja ei voi muokata."])
           [tallenna e! tallenna-app {:kayttaja kayttaja
                                      :urakka-id (:id urakka)
                                      :valmis-tallennettavaksi? valmis-tallennettavaksi?}]])))))
