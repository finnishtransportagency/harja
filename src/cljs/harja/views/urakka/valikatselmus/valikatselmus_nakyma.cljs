(ns harja.views.urakka.valikatselmus.valikatselmus-nakyma
  (:require [tuck.core :as tuck]
            [harja.domain.roolit :as roolit]
            [harja.domain.lupaus-domain :as lupaus-domain]
            [harja.fmt :as fmt]
            [harja.pvm :as pvm]
            [harja.tiedot.istunto :as istunto]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka.valikatselmus.valikatselmus-tiedot :as valikatselmus-tiedot]
            [harja.tiedot.urakka.kulut.yhteiset :as t-yhteiset]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.komponentti :as komp]
            [harja.ui.debug :as debug]
            [harja.ui.yleiset :as yleiset]
            [harja.views.urakka.valikatselmus.yhteiset :as valikatselmus-yhteiset]
            [harja.views.urakka.valikatselmus.yhteenvetolaatikko :as yhteevetolaatikko]
            [harja.views.urakka.valikatselmus.tavoitehinnan-muutokset :as tavoitehinnan-muutokset]
            [harja.views.urakka.valikatselmus.lupaukset :as lupaukset]
            [harja.views.urakka.valikatselmus.hintapaatokset :as hintapaatokset]
            [harja.views.urakka.valikatselmus.raportit :as raportit]
            [harja.views.urakka.valikatselmus.hoidonjohtopalkkio :as hoidonjohtopalkkio]
            [harja.views.urakka.valikatselmus.hoitovuoden-lopun-hinnat :as hoitovuoden-lopun-hinnat]
            [harja.views.urakka.valikatselmus.indeksikorjaus :as indeksikorjaus])
  (:require-macros [harja.tyokalut.ui :refer [for*]]))

(defn- onko-hoitokausi-tulevaisuudessa? [hoitokausi nykyhetki]
  (let [hoitokauden-alkuvuosi (pvm/vuosi (first hoitokausi))
        nykykuukausi (pvm/kuukausi nykyhetki)
        nykyvuosi (pvm/vuosi nykyhetki)
        vastaus (cond
                  ;; Alkaa samana vuonna, mutta ei olla vielä syksyssä tarpeeksi pitkällä
                  (and
                    (= hoitokauden-alkuvuosi nykyvuosi)
                    (< nykykuukausi 10))
                  true
                  ;; On alkanut aiempana vuonna
                  (< hoitokauden-alkuvuosi nykyvuosi)
                  false
                  ;; Alkaa myöhemmin vuoden perusteella
                  (> hoitokauden-alkuvuosi nykyvuosi)
                  true
                  (and
                    (= hoitokauden-alkuvuosi nykyvuosi)
                    (= nykykuukausi 10))
                  false
                  ;; Jää vain tapaukset, joissa hoitovuosi ei ole tulevaisuudessa
                  :else false)]
    vastaus))

(defn- onko-hoitokausi-urakkakauden-jalkeen? [hoitokausi urakan-loppuvuosi]
  (let [hoitokauden-loppuvuosi (pvm/vuosi (second hoitokausi))]
    (> hoitokauden-loppuvuosi urakan-loppuvuosi)))

(defn- onko-hoitokausi-ennen-urakkakautta? [hoitokausi urakan-alkuvuosi]
  (let [hoitokauden-alkuvuosi (pvm/vuosi (first hoitokausi))]
    (< hoitokauden-alkuvuosi urakan-alkuvuosi)))

(defn valikatselmus-otsikko-ja-tiedot [e! {:keys [tallennus-kesken?] :as app}]
  (let
    [urakan-nimi (:nimi @nav/valittu-urakka)
     valittu-hoitokausi (:valittu-hoitokausi app)
     urakan-alkuvuosi (pvm/vuosi (:alkupvm @nav/valittu-urakka))
     urakan-loppuvuosi (pvm/vuosi (:loppupvm @nav/valittu-urakka))
     urakan-kesto-vuosina (- urakan-loppuvuosi urakan-alkuvuosi)
     hoitokaudet (into [] (range urakan-alkuvuosi (+ urakan-kesto-vuosina urakan-alkuvuosi)))
     hoitokausi-tulevaisuudessa? (onko-hoitokausi-tulevaisuudessa? valittu-hoitokausi (pvm/nyt))
     hoitokausi-menneisyydessa? (valikatselmus-yhteiset/onko-hoitokausi-menneisyydessa? valittu-hoitokausi (pvm/nyt) urakan-alkuvuosi)
     jvh? (roolit/jvh? @istunto/kayttaja)]
    [:<>
     [:div.row
      [:div.col-xs-12.col-md-8.sivu-otsikko
       [:h1.valikatselmus "Välikatselmus"]
       [:div.urakan-nimi urakan-nimi]]
      [:div.col-xs-12.col-md-4.hoitovuosi-valikko
       [:div
        [:span.caption-small-strong.alasveto-label "Hoitovuosi"]
        [yleiset/livi-pudotusvalikko {:valinta (pvm/vuosi (first valittu-hoitokausi))
                                      :vayla-tyyli? true
                                      :disabled tallennus-kesken?
                                      :data-cy "hoitokausi-valinta"
                                      :valitse-fn #(do (e! (valikatselmus-tiedot/->ValitseHoitokausi (:id @nav/valittu-urakka) %))
                                                     (e! (t-yhteiset/->NollaaValikatselmuksenPaatokset)))
                                      :format-fn #(fmt/hoitokauden-jarjestysluku-ja-alku-ja-loppupvm % hoitokaudet "Hoitovuosi")
                                      :klikattu-ulkopuolelle-params {:tarkista-komponentti? true}}
         hoitokaudet]]]]

     ;; Varoitetaan kaikkia muita paitsi järjestelmävalvojaa, ettei välikatselmusta voida tehdä
     (when (and hoitokausi-tulevaisuudessa? (not jvh?))
       [:div.valikatselmus-tulevaisuudessa-varoitus
        [ikonit/harja-icon-status-alert]
        [:span "Hoitovuodelle ei voi tässä vaiheessa tehdä välikatselmusta."]])
     (when (and hoitokausi-menneisyydessa? (not jvh?))
       [:div.valikatselmus-menneisyydessa-varoitus
        [ikonit/harja-icon-status-alert]
        [:span "Hoitovuosi on lukittu vuoden vaihteessa ja välikatselmusta ei voi enää muokata."]])]))

(defn paatoskomponentit [e! {:keys [valittu-hoitokausi tallennus-kesken? paatokset] :as app}]
  (let [urakan-alkuvuosi (pvm/vuosi (-> @tila/yleiset :urakka :alkupvm))
        nykyhetki (pvm/nyt)
        poikkeusvuosi? (lupaus-domain/vuosi-19-20? urakan-alkuvuosi)
        hoitokauden-alkuvuosi (pvm/vuosi (first valittu-hoitokausi))
        avatut-paatokset (:avatut-paatokset app)
        oikeudet-muokata? (or (roolit/jvh? @istunto/kayttaja)
                            (and
                              (valikatselmus-yhteiset/onko-oikeudet-tehda-paatos? (-> @tila/yleiset :urakka :id))
                              (not (onko-hoitokausi-tulevaisuudessa? valittu-hoitokausi nykyhetki))
                              (or
                                poikkeusvuosi?
                                (not (valikatselmus-yhteiset/onko-hoitokausi-menneisyydessa? valittu-hoitokausi nykyhetki urakan-alkuvuosi)))))
        hoitovuosi-kesken? (pvm/valissa? nykyhetki (first valittu-hoitokausi) (second valittu-hoitokausi))
        hoitokausi-tulevaisuudessa? (onko-hoitokausi-tulevaisuudessa? valittu-hoitokausi (pvm/nyt))]
    [:<>
     (when (and (<= (count paatokset) 1) (or hoitovuosi-kesken? hoitokausi-tulevaisuudessa?))
       [:div.hoitovuosi-info
        (when hoitovuosi-kesken?
          [yleiset/info-laatikko :neutraali "Hoitovuosi ei ole vielä päättynyt."
           (str "Välikatselmuksen päätökset voi tallentaa 1.10. " (pvm/vuosi (second valittu-hoitokausi))" alkaen. "
             (when (<= urakan-alkuvuosi 2024) " Voit lisätä tavoitehinnan muutoksia myös kesken hoitovuoden.")) nil])
        (when hoitokausi-tulevaisuudessa?
          [yleiset/info-laatikko :neutraali "Hoitovuosi ei ole vielä alkanut." nil nil])])
     [:div
      (doall
        (for* [paatos paatokset]
          (cond
            (= (ffirst paatos) :lupaukset) [lupaukset/lupauspaatos e! (second (first paatos)) oikeudet-muokata? tallennus-kesken? hoitokauden-alkuvuosi avatut-paatokset]
            (= (ffirst paatos) :tavoitehinnan-muutokset)
            (if (>= urakan-alkuvuosi 2025)
              [:div {:style {:height "90px" :width "100%"}}
               [yleiset/info-laatikko :huolto "Tavoitehinnan muutokset uudistuvat. Päivityksen ajan osio on pois käytöstä. Tiedotamme muutoksesta tarkemmin sähköpostitse." nil nil nil]]
              [tavoitehinnan-muutokset/tavoitehinnan-muutokset e! (second (first paatos)) oikeudet-muokata? tallennus-kesken? avatut-paatokset (:tavoitehinnan-muutokset app) hoitovuosi-kesken?])
            (= (ffirst paatos) :tavoitehinnan-ylitys) [hintapaatokset/tavoitehinnan-ylitys e! (second (first paatos)) oikeudet-muokata? tallennus-kesken? avatut-paatokset]
            (= (ffirst paatos) :tavoitehinnan-alitus) [hintapaatokset/tavoitehinnan-alitus e! (second (first paatos)) tallennus-kesken? avatut-paatokset]
            (= (ffirst paatos) :kattohinnan-ylitys) [hintapaatokset/kattohinnan-ylitys e! (second (first paatos)) oikeudet-muokata? tallennus-kesken? avatut-paatokset]
            (= (ffirst paatos) :valikatselmuspoytakirjaan-liitettavat-raportit) [raportit/raportit e! (second (first paatos)) tallennus-kesken? hoitokauden-alkuvuosi avatut-paatokset]
            (= (ffirst paatos) :hoitovuoden-lopun-indeksikorjaus) [indeksikorjaus/paatos e! (second (first paatos)) oikeudet-muokata? tallennus-kesken? avatut-paatokset]
            (= (ffirst paatos) :hoidonjohtopalkkion-muutos) [hoidonjohtopalkkio/paatos e! (second (first paatos)) oikeudet-muokata? tallennus-kesken? avatut-paatokset]
            (= (ffirst paatos) :hoitovuoden-lopun-tavoite-ja-kattohinta) [hoitovuoden-lopun-hinnat/paatos e! (second (first paatos)) oikeudet-muokata? tallennus-kesken? avatut-paatokset]
            :else nil)))]]))

(defn- varmista-hoitokauden-alkuvuosi [valittu-hoitokausi]
  (let [hoitokausi-urakkakauden-jalkeen? (when-not (nil? valittu-hoitokausi) (onko-hoitokausi-urakkakauden-jalkeen? valittu-hoitokausi (pvm/vuosi (-> @tila/yleiset :urakka :loppupvm))))
        hoitokausi-ennen-urakkakautta? (when-not (nil? valittu-hoitokausi) (onko-hoitokausi-ennen-urakkakautta? valittu-hoitokausi (pvm/vuosi (-> @tila/yleiset :urakka :alkupvm))))]

    (cond
      (and (nil? valittu-hoitokausi) (pvm/jalkeen? (pvm/nyt) (-> @tila/yleiset :urakka :loppupvm)))
      (dec (pvm/vuosi (-> @tila/yleiset :urakka :loppupvm)))

      (and (nil? valittu-hoitokausi) (pvm/ennen? (pvm/nyt) (-> @tila/yleiset :urakka :alkupvm)))
      (pvm/vuosi (-> @tila/yleiset :urakka :alkupvm))

      (and (not hoitokausi-ennen-urakkakautta?) (not hoitokausi-urakkakauden-jalkeen?) (not (nil? valittu-hoitokausi)))
      (pvm/vuosi (first valittu-hoitokausi))

      (and (nil? valittu-hoitokausi) (pvm/valissa? (pvm/nyt) (-> @tila/yleiset :urakka :alkupvm) (-> @tila/yleiset :urakka :loppupvm)))
      ;; Jos ollaan hoitokauden alussa eli 10,11,12 kuukaudessa, niin annetaan vuosi. Muuten pudotetaan siitä yksi vuosi pois.
      (if (>= (pvm/kuukausi (pvm/nyt)) 10)
        (pvm/vuosi (pvm/nyt))
        (dec (pvm/vuosi (pvm/nyt))))

      :else
      (if hoitokausi-urakkakauden-jalkeen?
        (dec (pvm/vuosi (-> @tila/yleiset :urakka :loppupvm)))
        (pvm/vuosi (-> @tila/yleiset :urakka :alkupvm))))))

(defn valikatselmus* [e! app]
  (komp/luo
    (komp/lippu valikatselmus-tiedot/valikatselmus-nakymassa?)
    (komp/piirretty (fn [this]
                      (let [{:keys [valittu-kuukausi valittu-hoitokausi]} app
                            valittu-urakka-id @nav/valittu-urakka-id
                            ;; Varmista, että hoitokauden-alkuvuosi on urakan alkupäivän ja loppupäivän välissä
                            hoitokauden-alkuvuosi (varmista-hoitokauden-alkuvuosi valittu-hoitokausi)]
                        (e! (valikatselmus-tiedot/->HaeValikatselmuksenTiedot valittu-urakka-id hoitokauden-alkuvuosi)))))
    (fn [e! app]
      (let [hoitokauden-alkuvuosi (varmista-hoitokauden-alkuvuosi (:valittu-hoitokausi app))
            app (assoc app :hoitokauden-alkuvuosi hoitokauden-alkuvuosi)
            app (assoc app :valittu-hoitokausi [(pvm/hoitokauden-alkupvm hoitokauden-alkuvuosi)
                                                (pvm/paivan-lopussa (pvm/hoitokauden-loppupvm (inc hoitokauden-alkuvuosi)))])]
        [:div {:id "vayla"}
         (if (:haku-kaynnissa? app)
           [:div.valikatselmus-haku
            [yleiset/ajax-loader-pieni "Haetaan välikatselmuksen tietoja..."]]
           [:div.valikatselmus-container
            #_ [debug/debug app]
            [:div.col-xs-12.col-md-7

             [valikatselmus-otsikko-ja-tiedot e! app]
             [:div.paatokset
              [paatoskomponentit e! app]]]
            [:div.col-xs-12.col-md-5
             [:div.yhteenveto-container
              [yhteevetolaatikko/yhteenvetolaatikko e! app]]]])]))))

(defn valikatselmus []
  (tuck/tuck tila/valikatselmus valikatselmus*))
