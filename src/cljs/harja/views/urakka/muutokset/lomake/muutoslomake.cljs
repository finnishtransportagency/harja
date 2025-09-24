(ns harja.views.urakka.muutokset.lomake.muutoslomake
  "Muutokset välilehden lomakkeet (Lisäys / Muokkaus)"
  (:require [clojure.string :as str]

            [taoensso.timbre :as log]
            [harja.tiedot.urakka.muutokset.kirjatut-muutokset-tiedot :as t-kirjatut]
            [harja.ui.napit :as napit]
            [harja.ui.lomake :as lomake]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.navigaatio :as nav]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.domain.muutos-domain :as muutos-domain]
            [harja.views.urakka.muutokset.yhteiset :as yhteiset]
            [harja.tiedot.urakka.muutokset.yhteiset-tiedot :as t-yhteiset]
            [harja.ui.yleiset :as yleiset]


    ;; Lomake tyypit, näitä voi lisäillä tarvittaessa
            [harja.views.urakka.muutokset.lomake.lomake-pysyva :as pysyva]
            [harja.views.urakka.muutokset.lomake.lomake-johto-hallinto :as johto-ja-hallinto]
            [harja.views.urakka.muutokset.lomake.lomake-muutostyo :as muutostyo]
            [reagent.core :as r]))


(defn- lomakkeen-footer [muutos tyyppi e!
                         {:keys [tallennus-kesken? voi-tallentaa? 
                                 tallenna-painettu? lomake-virheet] :as _app}]
  [:div
   [:hr]
   (when (and 
           tallenna-painettu? 
           (seq lomake-virheet))
     [yleiset/nayta-virheet :varoitus lomake-virheet])

   ;; Muutostyö lomakkeeseen design mukaan infolaatikko
   (when (= tyyppi "muutostyo")
     [yleiset/info-laatikko :neutraali
      "Tallentamisen jälkeen muutostyölle voi kohdistaa kuluja."
      nil nil
      {:luokka "perustiedot"}])

   [napit/tallenna "Tallenna"
    #(do
       (t-yhteiset/scrollaa-viimeksi-valitulle-riville)
       (tuck-apurit/e-kanavalla! e! t-yhteiset/->TallennaMuutos muutos))
    ;; Saavutettavuusmielessä, halutaan näyttää virheet vasta, kun tallenna nappia painettu 
    ;; Tallenna nappi on myös disabled alkutilassa, kun lomaketta ei ole muokattu 
    {:disabled (boolean (and tallenna-painettu? (not voi-tallentaa?)))}]

   [napit/peruuta "Peruuta"
    #(do
       (t-yhteiset/scrollaa-viimeksi-valitulle-riville)
       (e! (t-yhteiset/->MuokkaaMuutosta nil)))
    {:disabled tallennus-kesken?}]])

(defn- alusta-lomakkeen-pohjatiedot [e! muutostyyppi valittu-hoitokausi rivi]
  (log/debug "Haetaan lomakkeen pohjatiedot muutostyypille:" muutostyyppi)

  (let [rivi (case muutostyyppi
               "pysyva" (do
                          (e! (t-kirjatut/->HaePysyvanMuutoksenPohjatiedotLomakkeelle))
                          rivi)
               ;; TODO: Tämä on jäänne muutosten ensimmäisen version ajalta, ja poikkeaa muusta logiikasta
               ;;       Vaatisi refaktorointia, jotta logiikka lomakkeen alustamiselle olisi yhtenäisempi
               "johto-ja-hallintokorvaus" (assoc rivi
                                            :johto-ja-hallintokorvaukset
                                            (t-yhteiset/johto-ja-hallintokorvausmuutoksen-rivit valittu-hoitokausi []))
               rivi)]
    rivi))

(defn- lomakkeen-tyyppivalinta
  [e! {:keys [valittu-hoitokausi] :as _app}]
  (vec
    (keep identity
      (concat
        [{:otsikko "Tyyppi"
          :nimi :tyyppi
          :pakollinen? true
          :aseta (fn [rivi arvo]
                   (->>
                     ;; Aseta valittu tyyppi
                     (assoc rivi :tyyppi arvo)
                     ;; Haetaan pohjatietoja tietyille lomaketyypeille, kun uutta muutosta luodaan
                     (alusta-lomakkeen-pohjatiedot e! arvo valittu-hoitokausi)))
          ;; Sallitaan muokkaus vain uudelle muutokselle
          :muokattava? #(nil? (:id %))
          :kaariva-luokka "muutostyyppivalinta"
          :tyyppi :valinta
          :vayla-tyyli? true
          :valinnat muutos-domain/+muutostyypit-lomakkeella+
          :valinta-arvo identity
          :valinta-nayta (fn [arvo]
                           (muutos-domain/tyyppi-fmt arvo (:sopimustyyppi @nav/valittu-urakka)))
          :uusi-rivi? true
          ::lomake/col-luokka "perustiedot col-sm-6"}]))))


(defn muutoslomake [e! {:keys [muokattava-muutos] :as _app}]
  (komp/luo
    (komp/sisaan-ulos
      #(e! (t-yhteiset/->HaeMuutoksenTiedot muokattava-muutos))
      #(e! (t-yhteiset/->MuokkaaMuutosta nil)))

    (fn [e! {:keys [muokattava-muutos] :as app}]
      [:span.muutoslomake

       [lomake/lomake
        {:otsikko (if (:id muokattava-muutos) "Muokkaa muutosta" "Lisää uusi muutos")
         :tarkkaile-ulkopuolisia-muutoksia? true
         :muokkaa! #(e! (t-yhteiset/->PaivitaLomake %))
         :footer-fn (fn [muutos] (lomakkeen-footer muutos (:tyyppi muokattava-muutos) e! app))}

        ;; Tähän lomakkeiden muutostyyppikohtaiset skeemat
        (into []
          (concat
            (lomakkeen-tyyppivalinta e! app)

            (case (:tyyppi muokattava-muutos)
              "erillisrahoitettu" (yhteiset/lomake-yhteinen e! app)
              "johto-ja-hallintokorvaus" (johto-ja-hallinto/lomake-johto-ja-hallintokorvaus e! app)
              "maarapoikkeama" (yhteiset/lomake-yhteinen e! app)
              "pysyva" (pysyva/lomake-pysyva e! app)
              "toteutuneet-maarat" (yhteiset/lomake-yhteinen e! app)
              "muutostyo" (muutostyo/lomake-muutostyo e! app)

              nil [(lomake/ryhma {:otsikko "Valitse tyyppi"})]

              ;; Default - jos mikään ylläolevista ei osu
              [(lomake/ryhma {:otsikko "Sisältöä ei saatavilla."})])))
        muokattava-muutos]])))
