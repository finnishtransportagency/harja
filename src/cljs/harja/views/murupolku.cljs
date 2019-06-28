(ns harja.views.murupolku
  "Murupolku on sovelluksenlaajuinen navigaatiokomponentti.
  Sen avulla voidaan vaikuttaa sovelluksen tilaan muun muassa
  seuraavia parametrejä käyttäen: väylämuoto, hallintayksikkö,
  urakka, urakan tyyppi, urakoitsija."
  (:require [reagent.core :refer [atom] :as reagent]
            [tuck.core :as tuck]
            [harja.ui.yleiset :refer [ajax-loader linkki alasveto-ei-loydoksia livi-pudotusvalikko]]
            [harja.ui.valinnat :as valinnat]
            [harja.ui.komponentti :as komp]
            [harja.ui.dom :as dom]
            [harja.loki :refer [log]]
            [harja.tiedot.istunto :as istunto]
            [harja.tiedot.urakoitsijat :as urakoitsijat]
            [harja.tiedot.hallintayksikot :as hal]
            [harja.tiedot.navigaatio :as nav]
            [harja.asiakas.tapahtumat :as t]
            [harja.tiedot.navigaatio.reitit :as reitit]
            [harja.tiedot.tilannekuva.tilannekuva :as tkuva]
            [harja.pvm :as pvm]
            [harja.domain.oikeudet :as oikeudet]
            [harja.domain.roolit :as roolit]
            [harja.tilanhallinta.tila :as tila]
            [taoensso.timbre :as tlog]
            [harja.domain.ely :as ely]))

(defrecord SuljeAlasveto [])
(defrecord AvaaAlasveto [tila])
(defrecord Valitse [valinta valittu?])

(extend-protocol tuck/Event
  SuljeAlasveto
  (process-event [_ app]
    (log "Sulje " app)
    (assoc app
      :valinta-auki false))
  Valitse
  (process-event [{valinta :valinta valittu? :valittu?} app]
    (log "Valitse " app)
    (assoc app :valinta valinta))
  AvaaAlasveto
  (process-event [{tila :tila} app]
    (log "Avaa " app)
    (do
      (log "Loggaan " tila)
      (assoc app
        :valinta-auki tila))))

(defn koko-maa []
  [:li
   [:a.murupolkuteksti {:href "#"
                        :style (when (nil? @nav/valittu-hallintayksikko)
                                 {:text-decoration "none"
                                  :color "#323232"})
                        :on-click #(do
                                    (.preventDefault %)
                                    (nav/valitse-hallintayksikko! nil))}
    "Koko maa"]])

(defn hallintayksikko [e! valinta-auki]
  (let [valittu @nav/valittu-hallintayksikko]
    [:li.dropdown.livi-alasveto {:class (when (= :hallintayksikko valinta-auki) "open")}

     (let [vu @nav/valittu-urakka]
       (if (or (not (nil? vu)) (= valinta-auki :hallintayksikko))
         [:a.murupolkuteksti {:href "#"
                              :on-click #(do
                                          (.preventDefault %)
                                          (nav/valitse-hallintayksikko! valittu))}
          (str (or (:nimi valittu) "- Hallintayksikkö -") " ")]

         [:span.valittu-hallintayksikko.murupolkuteksti (or (:nimi valittu) "- Hallintayksikkö -") " "]))

     [:button.nappi-murupolkualasveto.dropdown-toggle
      {:on-click #(when-not (= valinta-auki :hallintayksikko) (e! (->AvaaAlasveto :hallintayksikko)))}
      ;{:on-click #(swap! valinta-auki
      ;                   (fn [v]
      ;                     (if (= v :hallintayksikko)
      ;                       nil
      ;                       :hallintayksikko)))}
      [:span.livicon-chevron-down]]

     ;; Alasvetovalikko yksikön nopeaa vaihtamista varten
     [:ul.dropdown-menu.livi-alasvetolista {:role "menu"}
      (for [muu-yksikko (filter #(not= % valittu) @hal/vaylamuodon-hallintayksikot)]
        ^{:key (str "hy-" (:id muu-yksikko))}
        [:li.harja-alasvetolistaitemi
         [linkki (hal/elynumero-ja-nimi muu-yksikko)
          #(do (e! (->SuljeAlasveto))
               (nav/valitse-hallintayksikko! muu-yksikko))]])]]))

(defn urakka [e! valinta-auki]
  (let [valittu @nav/valittu-urakka]
    [:li.dropdown.livi-alasveto {:class (str (when (= :urakka valinta-auki) "open")
                                             (when-not @nav/valittu-hallintayksikko " disabled"))}
     [:span.valittu-urakka.murupolkuteksti (or (:nimi valittu) "- Urakka -") " "]

     [:button.nappi-murupolkualasveto.dropdown-toggle {:disabled (not
                                                                   (boolean @nav/valittu-hallintayksikko))
                                                       :on-click #(e! (->AvaaAlasveto (when-not (= valinta-auki :urakka) :urakka)))
                                                       ;:on-click #(swap! valinta-auki
                                                       ;                  (fn [v]
                                                       ;                    (if (= v :urakka)
                                                       ;                      nil
                                                       ;                      :urakka)))
                                                       }
      [:span.livicon-chevron-down]]

     ;; Alasvetovalikko urakan nopeaa vaihtamista varten
     [:ul.dropdown-menu.livi-alasvetolista {:role "menu"}

      (let [muut-kaynnissaolevat-urakat (sort-by :nimi
                                                 (filter #(and
                                                            (not= % valittu)
                                                            (pvm/jalkeen? (:loppupvm %) (pvm/nyt)))
                                                         @nav/suodatettu-urakkalista))]
        (if (empty? muut-kaynnissaolevat-urakat)
          [alasveto-ei-loydoksia "Tästä hallintayksiköstä ei löydy muita urakoita, joita on oikeus tarkastella."]

          (for [urakka muut-kaynnissaolevat-urakat]
            ^{:key (str "urakka-" (:id urakka))}
            [:li.harja-alasvetolistaitemi {:class (when-not @nav/valittu-hallintayksikko "disabled")
                                           :disabled (not
                                                       (boolean @nav/valittu-hallintayksikko)) } [linkki (:nimi urakka) #(nav/valitse-urakka! urakka)]])))]]))

(defn valinta [k v]
  ; (tlog/info "Luodaan valinta " k v (keys k))
  {:valittu? v
   :otsikko (:otsikko k)
   :lasti k})

(defn kategoria [nimi tunnus valinnat]
  {:kategoria           nimi
   :tunnus              tunnus
   :kategorian-valinnat (map (fn [m] (let [k (get valinnat m)]
                                       (if (number? m)
                                        (kategoria (ely/elynumero->nimi m) m k)
                                        (valinta m k)))) (keys valinnat))})

(defn suodattimet->kategoriat [suodattimet]
  ;(tlog/info "Suodattimet->Kat" suodattimet)
  (let [tyypit (keys (select-keys suodattimet [:paallystys :hoito :valaistus]))]
    ;(tlog/info "Käsitellään" tyypit)
    {:kategoriat (into [] (map
                            (fn [k]
                              (let [contents (get suodattimet k)]
                                ;(tlog/info "Käsittelen" k)
                                (kategoria (name k) k contents)))
                            tyypit))}))

(defn lisaa-urakka
  [e! app]
  [:li.dropdown.livi-alasveto
   [valinnat/kategorisoitu-checkbox-pudotusvalikko (suodattimet->kategoriat app) (fn [suodatin tila polku] (e! (tkuva/->AsetaAluesuodatin suodatin tila polku)))]])

(defn urakoitsija []
  [:div.murupolku-urakoitsija
   ;;[:div.livi-valikkonimio.murupolku-urakoitsija-otsikko "Urakoitsija"]
   [livi-pudotusvalikko {:valinta @nav/valittu-urakoitsija
                         :format-fn #(if % (:nimi %) "Kaikki")
                         :valitse-fn nav/valitse-urakoitsija!
                         :placeholder "Urakoitsija"
                         :class (str "alasveto-urakoitsija"
                                     (when (boolean @nav/valittu-urakka) " disabled"))
                         :disabled (or (some? @nav/valittu-urakka)
                                       (= (:sivu @reitit/url-navigaatio) :raportit))}
    (vec (conj (into [] (case (:arvo @nav/urakkatyyppi)
                          :kaikki @urakoitsijat/urakoitsijat-kaikki
                          :hoito @urakoitsijat/urakoitsijat-hoito
                          :paallystys @urakoitsijat/urakoitsijat-paallystys
                          :tiemerkinta @urakoitsijat/urakoitsijat-tiemerkinta
                          :valaistus @urakoitsijat/urakoitsijat-valaistus
                          :vesivayla @urakoitsijat/urakoitsijat-vesivaylat

                          @urakoitsijat/urakoitsijat-hoito)) ;;defaulttina hoito
               nil))]])

(defn urakkatyyppi []
  [:div.murupolku-urakkatyyppi
   ;;[:div.livi-valikkonimio.murupolku-urakkatyyppi-otsikko "Urakkatyyppi"]
   [livi-pudotusvalikko {:valinta @nav/urakkatyyppi
                         :format-fn #(if % (:nimi %) "Kaikki")
                         :valitse-fn nav/vaihda-urakkatyyppi!
                         :class (str "alasveto-urakkatyyppi" (when (boolean @nav/valittu-urakka) " disabled"))
                         :disabled (boolean @nav/valittu-urakka)
                         :data-cy "murupolku-urakkatyyppi"}
    nav/+urakkatyypit-ja-kaikki+]])

(defn murupolku
  "Itse murupolkukomponentti joka sisältää html:n"
  [e! _]
  (komp/luo
    (komp/kuuntelija
      [:hallintayksikko-valittu :hallintayksikkovalinta-poistettu
       :urakka-valittu :urakkavalinta-poistettu]
      #(e! (->SuljeAlasveto))
      ;; FIXME Tässä voisi käyttää (komp/klikattu-ulkopuolelle #(reset! valinta-auki false))
      ;; Mutta aiheuttaa mystisen virheen kun raporteista poistutaan
      :body-klikkaus
      (fn [this {klikkaus :tapahtuma}]
        (when-not (dom/sisalla? this klikkaus)
          (e! (->SuljeAlasveto)))))
    {:component-did-update (fn [_]
                             (t/julkaise! {:aihe      :murupolku-naytetty-domissa?
                                           :naytetty? @nav/murupolku-nakyvissa?}))}
    (fn [e! {:keys [murupolku navigaatio aluesuodattimet] :as app-state}]
      [:div
       [tila/state-of-the-atom app-state]
       (let [{:keys [valinta-auki valinnat]} murupolku
             ur @nav/valittu-urakka
             ei-urakkaa? (nil? ur)
             urakoitsija? (= (roolit/osapuoli @istunto/kayttaja) :urakoitsija)]
         (log "Valinta " valinta-auki valinnat aluesuodattimet)
         [:span {:class (when (empty? @nav/tarvitsen-isoa-karttaa)
                          (if @nav/murupolku-nakyvissa?
                            ""
                            "hide"))}
          (if ei-urakkaa?
            [:ol.murupolku
             [koko-maa] [hallintayksikko e! valinta-auki] [urakka e! valinta-auki] [lisaa-urakka e! aluesuodattimet]
             (when-not urakoitsija?
               [urakoitsija])
             [urakkatyyppi]]
            [:ol.murupolku
             [koko-maa] [hallintayksikko valinta-auki] [urakka valinta-auki]])])])))
