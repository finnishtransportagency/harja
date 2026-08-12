(ns harja.views.urakkatilanne.sarakkeet.valikatselmus
  (:require [harja.pvm :as pvm]
            [harja.ui.yleiset :as yleiset]
            [harja.tiedot.urakka.siirtymat :as siirtymat]
            [harja.tiedot.urakkatilanne.kojelauta :as tiedot]
            [harja.domain.kulut.kustannusten-seuranta :as kustannusten-seuranta-tiedot]))


(defn valikatselmus-sarake
  [rivi]
  (let [{:keys [tehdyt-paatokset-count mahdolliset-paatokset-count
                urakan_alkuvuosi tavoitehintaalituspaatos tavoitehintaylityspaatos kattohintapaatos
                lupauspaatos hoitokauden_alkuvuosi tavoitehinnan_muutospaatos]} rivi
        edellisen-hoitokauden-alkuvuosi (- (pvm/vuosi (first (pvm/paivamaaran-hoitokausi (pvm/nyt)))) 1)
        ;; 15.11. on takaraja, edellisen hoitokauden välikatselmus pitää olla tehtynä (edellinen --> kuluva hk -1)
        valikatselmuksen-takaraja-ohi? (or
                                         (< hoitokauden_alkuvuosi edellisen-hoitokauden-alkuvuosi)
                                         (and
                                           (= hoitokauden_alkuvuosi edellisen-hoitokauden-alkuvuosi)
                                           (pvm/jalkeen? (pvm/nyt)
                                             (kustannusten-seuranta-tiedot/valikatselmuksen-takarajapvm (+ hoitokauden_alkuvuosi 1)))))]
    [yleiset/wrap-if true
     [yleiset/tooltip {} :% "Siirry välikatselmukseen"]
     [:a.klikattava.alleviivaa {:href (str "/#urakat/valikatselmus?&hy=" (:evk_id rivi) "&u=" (:id rivi))
                                :on-click #(siirtymat/avaa-valikatselmus
                                             (:evk_id rivi) (:id rivi)
                                             [(pvm/hoitokauden-alkupvm (:hoitokauden_alkuvuosi rivi))
                                              (pvm/hoitokauden-loppupvm (inc (:hoitokauden_alkuvuosi rivi)))])}

      [:div.tavoitehintapaatos
       (yleiset/tila-indikaattori "kesken" {:fmt-fn (constantly "Kesken (3/7)")})]

      #_[:div.tavoitehintapaatos
         (if (and (nil? tavoitehinnan_muutospaatos) (nil? tavoitehinnan_muutospaatos))
           (yleiset/tila-indikaattori (if valikatselmuksen-takaraja-ohi? "hylatty" "kesken")
             {:fmt-fn (constantly
                        "Ei tavoitehinnan\u00ADmuutospäätöstä")})
           (yleiset/tila-indikaattori "valmis"
             {:fmt-fn (constantly
                        (kustannusten-seuranta-tiedot/valikatselmuksen-paatostyypin-nimi tavoitehinnan_muutospaatos))}))]

      #_[:div.tavoitehintapaatos
         (if (and (nil? tavoitehintaalituspaatos) (nil? tavoitehintaylityspaatos))
           (yleiset/tila-indikaattori (if valikatselmuksen-takaraja-ohi? "hylatty" "kesken")
             {:fmt-fn (constantly "Ei tavoitehinta\u00ADpäätöstä")})
           (yleiset/tila-indikaattori "valmis"
             {:fmt-fn (constantly
                        (kustannusten-seuranta-tiedot/valikatselmuksen-paatostyypin-nimi
                          (or tavoitehintaalituspaatos tavoitehintaylityspaatos)))}))]

      ;; Ennen vuotta 2021 alkaneissa urakoissa kattohintapäätös ei ollut kytköksissä tavoitehintapäätökseen, niin näytetään tämä tieto vain silloin
      ;; 2021 ja jälkeen riittää kertoa onko kattohintapäätös tehty. Jos sitä ei ole aloitettu, niin ei oleteta, että se tehdään
      #_[:div.kattohintapaatos
         (when (and (> urakan_alkuvuosi tiedot/+kattohintapaatos-kynnysvuosi+) (not (nil? kattohintapaatos)))
           (yleiset/tila-indikaattori "valmis"
             {:fmt-fn (constantly
                        (kustannusten-seuranta-tiedot/valikatselmuksen-paatostyypin-nimi kattohintapaatos))}))]

      #_[:div.lupauspaatokset
         (if (nil? lupauspaatos)
           (yleiset/tila-indikaattori (if valikatselmuksen-takaraja-ohi? "hylatty" "kesken")
             {:fmt-fn (constantly
                        "Ei lupaus\u00ADpäätöksiä")})
           [:span
            (yleiset/tila-indikaattori "valmis"
              {:fmt-fn (constantly
                         (kustannusten-seuranta-tiedot/valikatselmuksen-paatostyypin-nimi lupauspaatos))})])]
      ;;
      ]]))
