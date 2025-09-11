(ns harja.views.urakka.muutokset.lomake.muutoslomake
  "Muutokset välilehden lomakkeet (Lisäys / Muokkaus)"
  (:require [clojure.string :as str]

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
            [harja.views.urakka.muutokset.lomake.lomake-muutostyo :as muutostyo]))


(defn- lomakkeen-footer [muutos tyyppi
                         e! {:keys [tallennus-kesken? muokattava-muutos] :as _app}]
  [:div
   [:hr]
   (when-not (empty? (:puuttuvat-pakolliset-kentat muokattava-muutos))
     [yleiset/info-laatikko :varoitus
      (str
        "Lomakkeelta puuttuu pakollisia kenttiä: "
        (str/join ", " (:puuttuvat-pakolliset-kentat muokattava-muutos))
        ". Korjaa ne ja yritä uudelleen.")])

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
    ;; Älä anna tallentaa, jos tietoja syöttämättä
    {:disabled (or (lomake/virheita? muutos) tallennus-kesken?)}]

   [napit/peruuta "Peruuta"
    #(do
       (t-yhteiset/scrollaa-viimeksi-valitulle-riville)
       (e! (t-yhteiset/->MuokkaaMuutosta nil)))
    {:disabled tallennus-kesken?}]])


(defn- lomakkeen-tyyppivalinta
  [e! {:keys [valittu-hoitokausi] :as _app}]
  (vec
    (keep identity
      (concat
        [{:otsikko "Tyyppi"
          :nimi :tyyppi
          :pakollinen? true
          ;; Sallitaan muokkaus vain uudelle muutokselle
          :muokattava? #(nil? (:id %))
          :aseta (fn [rivi arvo]
                   (let [rivi (assoc rivi :tyyppi arvo)]
                     (t-yhteiset/alusta-tyyppikohtaisia-arvoja arvo valittu-hoitokausi)
                     rivi))
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
         :muokkaa! #(e! (t-yhteiset/->PaivitaLomake (lomake/ilman-lomaketietoja %)))
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

              nil  [(lomake/ryhma {:otsikko "Valitse tyyppi"})]

              ;; Default - jos mikään ylläolevista ei osu
              [(lomake/ryhma {:otsikko "Sisältöä ei saatavilla."})])))
        muokattava-muutos]])))
