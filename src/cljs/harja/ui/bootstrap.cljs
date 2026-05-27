(ns harja.ui.bootstrap
  "Common Bootstrap components for Reagent UI."
  (:require [reagent.core :refer [atom]]
            [harja.loki :refer [log]]
            [harja.ui.dom :as dom]
            [harja.ui.komponentti :as komp]
            [harja.ui.ikonit :as ikonit]
            [clojure.string :as clj-str]))


(defn- tabin-otsikko-teksti [title]
  (if (map? title)
    (:teksti title)
    title))

(defn- tabin-otsikko-sisalto [title]
  (if (map? title)
    (or (:sisalto title) (:teksti title))
    title))

(defn- tabin-data-cy [title classes]
  (or (when (map? title) (:data-cy title))
      (let [tabs-taso (re-find #"tabs-taso\d" (str classes))
            cy-title (-> (tabin-otsikko-teksti title)
                       str
                       (clj-str/replace #"ä" "a")
                       (clj-str/replace #"ö" "o"))]
        (if tabs-taso
          (str tabs-taso "-" cy-title)
          cy-title))))

(defn- tabs-tyyliluokka [style]
  (case (or style :tabs)
    :pills "harja-tabs-tyyli-pills"
    :tabs "harja-tabs-tyyli-tabs"))


(defn tabs
  "A tabbed panel. Takes a map of configuration parameters and alternating tab titles and tab components.
The following keys are supported in the configuration:

  :active     An atom containing the selected tab number. Defaults to (atom 0).
  :style      Tab style, either :pills or :tabs. Defaults to :tabs.
  :on-change  Optional callback function called when tab is changed. If provided,
              it will be called with the new tab keyword instead of directly
              resetting the :active atom. "

  [{:keys [active style classes on-change]} & alternating-title-and-component]
  (let [tarkista-aktiivinen-tabi (fn [active alternating-title-and-component]
                                   (let [tab-nimet (keep #(when (not (nil? (nth % 2)))
                                                            (nth % 1))
                                                     (partition 3 alternating-title-and-component))]
                                     (when-not (some #{@active} tab-nimet)
                                       (when-let [eka-tabi (first tab-nimet)]
                                         ;; Renderaa ensin nykyinen frame, ennen kuin kutsut reset!
                                         ;; Tästä tulee muuten stack-overflow
                                         (js/setTimeout #(reset! active eka-tabi) 0)))))]
    (tarkista-aktiivinen-tabi active alternating-title-and-component)
    (komp/luo
      (komp/kun-muuttuu (fn [{:keys [active style classes on-change]} & alternating-title-and-component]
                          (tarkista-aktiivinen-tabi active alternating-title-and-component)))
      (fn [{:keys [active style classes on-change]} & alternating-title-and-component]
        (let [style-class (tabs-tyyliluokka style)
              tabs (filter #(not (nil? (nth % 2)))
                     (partition 3 alternating-title-and-component))
              [active-tab-title active-tab-keyword active-component]
              (or (first (filter #(= @active (nth % 1)) tabs))
                (first tabs))
              vaihda-aktiivinen-tabi (fn [keyword event]
                                       (.preventDefault event)
                                       (if on-change
                                         (on-change keyword)
                                         (reset! active keyword)))]
          (if (empty? tabs)
            [:span "Ei käyttöoikeutta."]
            [:span
             [:ul {:class (clj-str/join " " (remove nil? ["harja-tabs" style-class classes]))
                   :role "tablist"}
              (for [[title keyword] tabs]
                ^{:key (str (tabin-otsikko-teksti title) "-" keyword)}
                [:li {:class (clj-str/join " " (remove nil? ["harja-tabs-valilehti"
                                                              (when (= keyword active-tab-keyword)
                                                                "harja-tabs-valittu")]))}
                 [:a.harja-tabs-linkki.klikattava (merge
                                                   {:tabIndex "0"
                                                    :role "tab"
                                                    :aria-selected (str (= keyword active-tab-keyword))
                                                    :on-click #(vaihda-aktiivinen-tabi keyword %)
                                                    :on-key-down #(when (dom/enter-nappain? %)
                                                                    (vaihda-aktiivinen-tabi keyword %))}
                                                   {:data-cy (tabin-data-cy title classes)})
                  (tabin-otsikko-sisalto title)]])]
             [:div.valilehti.harja-tabs-sisalto {:role "tabpanel"}
              active-component]]))))))

(defn navbar
  "A Bootstrap navbar component"
  [options header & items]
  (let [valikko-avattu? (atom false)
        toggle! #(swap! valikko-avattu? not)]
    (fn [options header & items]
      (let [[left-items _ right-items] (partition-by #(= :right %) items)
            data-cy (:data-cy options)
            sisalto-luokka (clj-str/join " " (remove nil? ["harja-navbar-sisalto"
                                                            (when @valikko-avattu?
                                                              "harja-navbar-sisalto-avoin")]))]
        [:nav {:role "navigation"
               :class (clj-str/join " " (remove nil? ["harja-navbar" (:luokka options)]))
               :data-cy data-cy}
         [:div.harja-navbar-runko
          [:div.harja-navbar-otsake
           [:button {:type "button"
                     :class "harja-navbar-vaihtaja"
                     :data-cy (when data-cy (str data-cy "-vaihtaja"))
                     :aria-expanded (str @valikko-avattu?)
                     :on-click toggle!}
            [:span.sr-only "Vaihda navigaatio"]
            [:span.harja-navbar-vaihtaja-viiva]
            [:span.harja-navbar-vaihtaja-viiva]
            [:span.harja-navbar-vaihtaja-viiva]]
           [:a.harja-navbar-brandi {:href "#"}
            header]]
          [:div {:class sisalto-luokka
                 :data-cy (when data-cy (str data-cy "-sisalto"))}
           (when left-items
             [:ul.harja-navbar-lista {:data-cy (when data-cy (str data-cy "-vasen"))}
              (for [item left-items]
                ^{:key (hash item)}
                [:li {:class (clj-str/join " " (remove nil? ["harja-navbar-item"
                                                              (:context (meta (first item)))]))}
                 item])])
           (when right-items
             [:ul.harja-navbar-lista.harja-navbar-lista-oikea {:data-cy (when data-cy (str data-cy "-oikea"))}
              (for [item right-items]
                ^{:key (hash item)}
                [:li {:class (clj-str/join " " (remove nil? ["harja-navbar-item"
                                                              (:context (meta (first item)))]))}
                 item])])]]]))))

(defn ^{:context "dropdown"}
  dropdown
  "A dropdown menu."
  [title items]
  (let [avoin? (atom false)
        vaihda-aukiolo! (fn [tapahtuma]
                          (.preventDefault tapahtuma)
                          (swap! avoin? not))
        sulje! (fn [_]
                 (reset! avoin? false))]
    (fn [title items]
      [:div {:class (clj-str/join " " (remove nil? ["harja-dropdown"
                                                     (when @avoin?
                                                       "harja-dropdown-avoin")]))}
       [:button {:type "button"
                 :class "harja-dropdown-vaihtaja"
                 :aria-expanded (str @avoin?)
                 :aria-haspopup "menu"
                 :on-click vaihda-aukiolo!
                 :on-key-down #(when (dom/esc-nappain? %)
                                 (sulje! %))}
        [:span.harja-dropdown-otsikko title]
        [:span.harja-dropdown-indikaattori {:aria-hidden true}]]
       (when @avoin?
         [:ul.harja-dropdown-valikko {:role "menu"}
          (for [item items]
            ^{:key (hash item)}
              [:li.harja-dropdown-kohta {:role "presentation"
                                         :on-click sulje!}
              item])])])))



(defn dropdown-panel
  "Panel with open/closed state that shows content only when open.
Opts can have the following keys:
   :open   an optional atom with boolean value for open/closed state, defaults to (atom false)
   :style  a style keyword :default, :primary, :success, :info :warning, :danger
  "
  [opts _ _]
  (let [open (or (:open opts) (atom false))
        style (or (:style opts) :default)
        data-cy (:data-cy opts)]
    (fn [_ title content]
      [:div.harja-panel.harja-dropdown-panel {:class (str "harja-dropdown-panel-tyyli-" (name style))
                                              :data-cy data-cy}
       [:button {:type "button"
                 :class "harja-panel-otsake harja-dropdown-panel-vaihtaja"
                 :data-cy (when data-cy (str data-cy "-vaihtaja"))
                 :aria-expanded (str @open)
                 :on-click #(swap! open not)}
        [:span.harja-dropdown-panel-otsikko title]
        [:span.harja-dropdown-panel-indikaattori {:aria-hidden true}
         (if @open
           [ikonit/livicon-minus]
           [ikonit/livicon-plus])]]

       (when @open
         [:div.harja-panel-runko {:data-cy (when data-cy (str data-cy "-sisalto"))}
          content])])))

(defn panel
  ([options content] (panel options nil content))
  ([options title content]
  [:div.harja-panel {:class (:class options)
               :data-cy (:data-cy options)}
    (when title
    [:div.harja-panel-otsake
     [:h1.musta title]])
   [:div.harja-panel-runko
     content]]))
