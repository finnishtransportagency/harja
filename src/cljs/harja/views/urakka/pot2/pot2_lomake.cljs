(ns harja.views.urakka.pot2.pot2-lomake
"POT2-lomake"
  (:require
    [reagent.core :refer [atom] :as r]
    [harja.asiakas.kommunikaatio :as k]
    [harja.domain.oikeudet :as oikeudet]
    [harja.domain.paallystys-ja-paikkaus :as paallystys-ja-paikkaus]
    [harja.domain.pot2 :as pot2-domain]
    [harja.domain.yllapitokohde :as yllapitokohteet-domain]
    [harja.loki :refer [log]]
    [harja.ui.debug :refer [debug]]
    [harja.ui.kentat :as kentat]
    [harja.ui.komponentti :as komp]
    [harja.ui.lomake :as lomake]
    [harja.ui.napit :as napit]
    [harja.ui.ikonit :as ikonit]
    [harja.ui.varmista-kayttajalta :as varmista-kayttajalta]
    [harja.ui.yleiset :refer [ajax-loader] :as yleiset]
    [harja.tiedot.navigaatio :as nav]
    [harja.tiedot.urakka.paallystys :as paallystys]
    [harja.tiedot.urakka.pot2.materiaalikirjasto :as mk-tiedot]
    [harja.tiedot.urakka.pot2.pot2-tiedot :as pot2-tiedot]
    [harja.views.urakka.pot2.alusta :as alusta]
    [harja.views.urakka.pot2.paallystekerros :as paallystekerros]
    [harja.views.urakka.pot-yhteinen :as pot-yhteinen]
    [harja.views.urakka.pot2.massa-lomake :as massa-lomake]
    [harja.views.urakka.pot2.murske-lomake :as murske-lomake])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]
                   [harja.atom :refer [reaction<!]]))


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
  "Urakan materiaalikirjasto on tyhjä. Aloita päällystysilmoitus lisäämällä urakalle materiaalit.")

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

(defn- materiaalit
  "Toimenpiteiden ja materiaalien otsikkorivi, jossa joitakin toimintoja"
  [e! massat murskeet]
  [:div
   [:h5 "Materiaalit"]
   (when (mk-tiedot/materiaalikirjasto-tyhja? massat murskeet)
     [:div {:style {:margin-top "24px"
                    :margin-bottom "24px"}}
      [yleiset/toast-viesti materiaalikirjasto-tyhja-txt]])
   [avaa-materiaalikirjasto-nappi #(e! (mk-tiedot/->NaytaModal true))]])

(defn lisatiedot
  [e! lisatiedot-atom]
  [:span
   [:h5 "Lisätiedot ja huomautukset"]
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
                     (e! (mk-tiedot/->HaePot2MassatJaMurskeet))
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
                                        #{:tekninen-osa :tila :versio})
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
           [pot-yhteinen/otsikkotiedot e! perustiedot urakka]
           [pot-yhteinen/paallystysilmoitus-perustiedot
            e! perustiedot-app urakka false muokkaa! pot2-validoinnit huomautukset]
           [:hr]
           [materiaalit e! massat murskeet]
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
                 [massa-lomake/massa-lomake e! massalomake-app]

                 (and pot2-murske-lomake (:sivulle? pot2-murske-lomake))
                 [murske-lomake/murske-lomake e! murskelomake-app ]

                 :else
                 [:span])
           [yleiset/valitys-vertical]
           [lisatiedot e! pot2-tiedot/lisatiedot-atom]
           [:hr]
           [pot-yhteinen/tallenna e! tallenna-app {:kayttaja kayttaja
                                                   :urakka-id (:id urakka)
                                                   :valmis-tallennettavaksi? valmis-tallennettavaksi?}]])))))
