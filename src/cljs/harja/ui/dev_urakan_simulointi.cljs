(ns harja.ui.dev-urakan-simulointi
  "Geneerinen kehitystyökalu urakan ja käyttäjän parametrien simulointiin."
  (:require [reagent.core :as r]
            [harja.pvm :as pvm]
            [harja.tiedot.navigaatio :as nav]))

(defonce override-tila
  (r/atom {:nayta-paneeli? false
           :nayta-skenaario-info? false
           :valittu-skenaario nil
           :urakan-overridet {}
           :kayttajan-overridet {}
           :aktiiviset-overridet #{}
           :muutos-kuuntelija nil}))

(defn aseta-muutos-kuuntelija!
  "Asettaa funktion jota kutsutaan kun override-arvot muuttuvat.
  Käytä tätä triggeröimään uudelleenhaku backendistä."
  [f]
  (swap! override-tila assoc :muutos-kuuntelija f))

(defn- triggeroi-muutos! []
  (when-let [f (:muutos-kuuntelija @override-tila)]
    (f)))

(defn aseta-override! [tyyppi avain arvo]
  (swap! override-tila
    (fn [tila]
      (-> tila
        (assoc-in [tyyppi avain] arvo)
        (update :aktiiviset-overridet conj avain))))
  (triggeroi-muutos!))

(defn poista-override! [avain]
  (swap! override-tila
    (fn [tila]
      (-> tila
        (update :urakan-overridet dissoc avain)
        (update :kayttajan-overridet dissoc avain)
        (update :aktiiviset-overridet disj avain))))
  (triggeroi-muutos!))

(defn nollaa-kaikki-overridet! []
  (let [kuuntelija (:muutos-kuuntelija @override-tila)]
    (reset! override-tila
      {:nayta-paneeli? (:nayta-paneeli? @override-tila)
       :nayta-skenaario-info? false
       :valittu-skenaario nil
       :urakan-overridet {}
       :kayttajan-overridet {}
       :aktiiviset-overridet #{}
       :muutos-kuuntelija kuuntelija}))
  (triggeroi-muutos!))

(defn toggle-paneeli! []
  (swap! override-tila update :nayta-paneeli? not))

(defn toggle-skenaario-info! []
  (swap! override-tila update :nayta-skenaario-info? not))

(defn urakan-arvo [avain urakka]
  (let [alkuperainen-arvo (get urakka avain)
        override (get-in @override-tila [:urakan-overridet avain])]
    (if (contains? (:aktiiviset-overridet @override-tila) avain)
      override
      alkuperainen-arvo)))

(defn kayttajan-rooli-override [rooli-keyword]
  (when (contains? (:aktiiviset-overridet @override-tila) :rooli)
    (= rooli-keyword (get-in @override-tila [:kayttajan-overridet :rooli]))))

;; Mahdolliset arvot eri parametreille
(def parametrien-mahdolliset-arvot
  {:alkuvuosi {:arvot [2019 2020 2021 2022 2023 2024 2025 2026]
               :kuvaus "Urakan alkuvuosi"}
   :erittain-vaativa-hoitourakka {:arvot [true false]
                                   :kuvaus "MHU+ vai MHU"}
   :tyyppi {:arvot [:teiden-hoito :hoito :paallystys :paikkaus :tiemerkinta]
            :kuvaus "Urakkatyyppi"}})

(defn luo-satunnainen-skenaario []
  (let [alkuvuosi (rand-nth (:arvot (:alkuvuosi parametrien-mahdolliset-arvot)))
        mhu-plus? (rand-nth (:arvot (:erittain-vaativa-hoitourakka parametrien-mahdolliset-arvot)))]
    {:urakan-overridet {:alkupvm (pvm/->pvm (str "1.10." alkuvuosi))
                        :loppupvm (pvm/->pvm (str "30.9." (+ alkuvuosi 5)))
                        :erittain-vaativa-hoitourakka mhu-plus?}
     :aktiiviset-overridet #{:alkupvm :loppupvm :erittain-vaativa-hoitourakka}
     :kuvaus (str "Satunnainen: " (if mhu-plus? "MHU+" "MHU") " " alkuvuosi)}))

(def esimerkkiskenaariot
  {"MHU 2019"
   {:urakan-overridet {:alkupvm (pvm/->pvm "1.10.2019")
                       :loppupvm (pvm/->pvm "30.9.2024")
                       :erittain-vaativa-hoitourakka false}
    :aktiiviset-overridet #{:alkupvm :loppupvm :erittain-vaativa-hoitourakka}
    :kuvaus "Vanha MHU-malli, alkuvuosi 2019"}
   "MHU+ 2024"
   {:urakan-overridet {:alkupvm (pvm/->pvm "1.10.2024")
                       :loppupvm (pvm/->pvm "30.9.2029")
                       :erittain-vaativa-hoitourakka true}
    :aktiiviset-overridet #{:alkupvm :loppupvm :erittain-vaativa-hoitourakka}
    :kuvaus "Erittäin vaativa hoitourakka (MHU+)"}
   "MHU 2025"
   {:urakan-overridet {:alkupvm (pvm/->pvm "1.10.2025")
                       :loppupvm (pvm/->pvm "30.9.2030")
                       :erittain-vaativa-hoitourakka false}
    :aktiiviset-overridet #{:alkupvm :loppupvm :erittain-vaativa-hoitourakka}
    :kuvaus "Uusin MHU-versio, alkuvuosi 2025"}
   "🎲 Satunnainen"
   {:fn luo-satunnainen-skenaario
    :kuvaus "Luo satunnainen skenaario testaukseen"}})

(defn lataa-skenaario! [nimi skenaario]
  (let [sk (if (:fn skenaario)
             ((:fn skenaario))
             skenaario)]
    (swap! override-tila merge
      {:urakan-overridet (:urakan-overridet sk)
       :kayttajan-overridet (:kayttajan-overridet sk)
       :aktiiviset-overridet (:aktiiviset-overridet sk)
       :valittu-skenaario {:nimi nimi :kuvaus (:kuvaus sk)}}))
  (triggeroi-muutos!))

(defn- pvm->input-arvo [pvm]
  (when pvm
    (let [v (pvm/vuosi pvm) k (pvm/kuukausi pvm) p (pvm/paiva pvm)]
      (str v "-" (when (< k 10) "0") k "-" (when (< p 10) "0") p))))

(defn- input-arvo->pvm [arvo]
  (when (and arvo (not= arvo "")) (pvm/->pvm arvo)))

(defn- skenaario-info-laatikko []
  (let [tila @override-tila
        urakka @nav/valittu-urakka
        ;; Pidetään auki automaattisesti jos valinta on tehty
        nayta-info? (or (:nayta-skenaario-info? tila)
                        (some? (:valittu-skenaario tila)))]
    [:div {:style {:background "#f0f8ff" :border "1px solid #0066cc"
                   :border-radius "4px" :padding "10px" :margin-bottom "12px"
                   :font-size "12px"}}
     [:div {:style {:display "flex" :justify-content "space-between"
                    :align-items "center" :margin-bottom "6px"}}
      [:strong "📊 Skenaario-info"]
      [:button {:on-click toggle-skenaario-info!
                :style {:background "none" :border "none" :cursor "pointer"
                        :font-size "14px" :padding "0"}}
       (if nayta-info? "▼" "▶")]]
     (when nayta-info?
       [:div
        (when-let [valittu (:valittu-skenaario tila)]
          [:div {:style {:margin-bottom "8px" :padding "6px"
                         :background "#e3f2fd" :border-radius "3px"}}
           [:strong "Valittu: "] (:nimi valittu)
           [:br]
           [:small (:kuvaus valittu)]])
        [:table {:style {:width "100%" :font-size "11px" :border-collapse "collapse"}}
         [:thead
          [:tr
           [:th {:style {:text-align "left" :padding "4px" :border-bottom "1px solid #ddd"}} "Parametri"]
           [:th {:style {:text-align "left" :padding "4px" :border-bottom "1px solid #ddd"}} "Oikea"]
           [:th {:style {:text-align "left" :padding "4px" :border-bottom "1px solid #ddd"}} "Override"]]]
         [:tbody
          [:tr
           [:td {:style {:padding "4px"}} "Alkupvm"]
           [:td {:style {:padding "4px"}} (pvm/pvm (:alkupvm urakka))]
           [:td {:style {:padding "4px"
                         :color (if (contains? (:aktiiviset-overridet tila) :alkupvm)
                                  "#dc3545" "#666")
                         :font-weight (if (contains? (:aktiiviset-overridet tila) :alkupvm)
                                        "bold" "normal")}}
            (if-let [override (get-in tila [:urakan-overridet :alkupvm])]
              (pvm/pvm override)
              "-")]]
          [:tr
           [:td {:style {:padding "4px"}} "Loppupvm"]
           [:td {:style {:padding "4px"}} (pvm/pvm (:loppupvm urakka))]
           [:td {:style {:padding "4px"
                         :color (if (contains? (:aktiiviset-overridet tila) :loppupvm)
                                  "#dc3545" "#666")
                         :font-weight (if (contains? (:aktiiviset-overridet tila) :loppupvm)
                                        "bold" "normal")}}
            (if-let [override (get-in tila [:urakan-overridet :loppupvm])]
              (pvm/pvm override)
              "-")]]
          [:tr
           [:td {:style {:padding "4px"}} "MHU+"]
           [:td {:style {:padding "4px"}} (if (:erittain-vaativa-hoitourakka urakka) "Kyllä" "Ei")]
           [:td {:style {:padding "4px"
                         :color (if (contains? (:aktiiviset-overridet tila) :erittain-vaativa-hoitourakka)
                                  "#dc3545" "#666")
                         :font-weight (if (contains? (:aktiiviset-overridet tila) :erittain-vaativa-hoitourakka)
                                        "bold" "normal")}}
            (if (contains? (:aktiiviset-overridet tila) :erittain-vaativa-hoitourakka)
              (if (get-in tila [:urakan-overridet :erittain-vaativa-hoitourakka]) "Kyllä" "Ei")
              "-")]]
          ]]])]))

(defn simulointi-paneeli []
  (let [tila @override-tila
        urakka @nav/valittu-urakka]
    [:div.dev-urakan-simulointi-paneeli
     {:style {:position "fixed" :top "80px" :right "20px"
              :background "white" :border "2px solid #0066cc"
              :border-radius "8px" :padding "20px" :z-index 9999
              :max-width "450px" :max-height "85vh"
              :overflow-y "auto" :overflow-x "hidden"
              :box-shadow "0 4px 12px rgba(0,0,0,0.15)"
              ;; Smooth scrolling ja parempi scrollbar
              :scroll-behavior "smooth"
              :webkit-overflow-scrolling "touch"}}
     [:h3 "🔧 Simulointi"]
     [:button {:on-click toggle-paneeli!
               :style {:float "right"}} "✕"]
     [:div {:style {:clear "both"}}]
     [:div {:style {:background "#e3f2fd" :padding "8px" :border-radius "4px"
                    :margin-bottom "10px" :font-size "12px"}}
      [:strong "ℹ️ Huom:"]
      "Korvataan arvot frontissa - ei käytetä backendiä simulointiin"]
     
     [skenaario-info-laatikko]
     
     (when (seq (:aktiiviset-overridet tila))
       [:button {:on-click nollaa-kaikki-overridet!
                 :style {:width "100%" :padding "8px" :margin-bottom "12px"
                         :background "#dc3545" :color "white" :border "none"
                         :border-radius "4px" :cursor "pointer"}}
        "🗑️ Nollaa kaikki overridet"])
     [:div {:style {:margin-bottom "15px"}}
      [:h4 {:style {:margin "0 0 8px 0" :font-size "14px"}} "Skenaariot"]
      (for [[nimi sk] esimerkkiskenaariot]
        ^{:key nimi}
        [:div {:style {:margin-bottom "6px"}}
         [:button {:on-click #(lataa-skenaario! nimi sk)
                   :style {:width "100%" :padding "10px" :text-align "left"
                           :background "#e7f3ff" :border "1px solid #0066cc"
                           :border-radius "4px" :cursor "pointer" :font-size "13px"}}
          [:div {:style {:font-weight "bold"}} nimi]
          [:div {:style {:font-size "11px" :color "#666" :margin-top "2px"}}
           (:kuvaus sk)]]])]
     [:div
      [:h4 "Urakan parametrit"]
      [:label "Alkupvm"
       [:input {:type "date"
                :value (pvm->input-arvo (get-in tila [:urakan-overridet :alkupvm]))
                :on-change #(let [v (input-arvo->pvm (.. % -target -value))]
                              (if v
                                (aseta-override! :urakan-overridet :alkupvm v)
                                (poista-override! :alkupvm)))}]]
      [:label
       [:input {:type "checkbox"
                :checked (boolean (get-in tila [:urakan-overridet :erittain-vaativa-hoitourakka]))
                :on-change #(if (.. % -target -checked)
                              (aseta-override! :urakan-overridet :erittain-vaativa-hoitourakka true)
                              (poista-override! :erittain-vaativa-hoitourakka))}]
       " MHU+"]]
     [:div
      [:h4 "Aktiiviset"]
      (if (empty? (:aktiiviset-overridet tila))
        [:p "Ei overrideja"]
        [:ul
         (for [a (:aktiiviset-overridet tila)]
           ^{:key a}
           [:li (str a)
            [:button {:on-click #(poista-override! a)} "X"]])])]]))

(defn simulointi-toggle-nappi []
  [:button {:on-click toggle-paneeli!
            :style {:position "fixed" :top "60px" :right "20px"
                    :background "#0066cc" :color "white"
                    :border-radius "50%" :width "50px" :height "50px"
                    :z-index 9998 :border "none" :cursor "pointer"}}
   "🔧"])

(defn simulointityokalu []
  (let [tila @override-tila]
    [:<>
     [simulointi-toggle-nappi]
     (when (:nayta-paneeli? tila)
       [simulointi-paneeli])]))
