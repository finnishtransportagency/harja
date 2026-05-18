(ns harja.ui.bootstrap
  "Common Bootstrap components for Reagent UI."
  (:require [reagent.core :refer [atom]]
            [harja.loki :refer [log]]
            [harja.ui.dom :as dom]
            [harja.ui.komponentti :as komp]
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
  (let [collapse-state (atom "collapse.in")
        toggle! #(swap! collapse-state
                   (fn [s]
                     (if (= s "collapse")
                       "collapse.in"
                       "collapse")))]
    (fn [options header & items]
      [:nav.navbar.navbar-default {:role "navigation"
                                   :class (:luokka options)}
       [:div.container-fluid

        ;; Brand and toggle get grouped for better mobile display
        [:div.navbar-header
         [:button.navbar-toggle.collapsed {:type "button"
                                           :on-click toggle!} ;; toggle collapse:  data-toggle="collapse" data-target="#bs-example-navbar-collapse-1">
          [:span.sr-only "Toggle navigation"]
          [:span.icon-bar]
          [:span.icon-bar]
          [:span.icon-bar]]
         [:a {:href "#"} header]]

        ;; Collect the nav links, forms, and other content for toggling
        (let [[left-items _ right-items] (partition-by #(= :right %) items)]
          [:div.navbar-collapse {:class @collapse-state}
           (when left-items
             [:ul.nav.navbar-nav
              (for [item left-items]
                ;;<li class="active"><a href="#">Link <span class="sr-only">(current)</span></a></li>
                ^{:key (hash item)}
                [:li {:class (str (when false "active")
                               " "
                               (:context (meta (first item))))} ;; context meta is for adapting parent container depending on child type
                 item])])
           (when right-items
             [:ul.nav.navbar-nav.navbar-right
              (for [item right-items]
                ^{:key (hash item)}
                [:li {:class (str (when false "active")
                               " "
                               (:context (meta (first item))))}
                 item])])])]])))

(defn ^{:context "dropdown"}
  dropdown
  "A dropdown menu."
  [title items]
  [:span
   [:a.dropdown-toggle {:role "button" :aria-expanded "false"}
    title [:span.caret]]
   [:ul.dropdown-menu {:role "menu"}
    (for [item items]
      [:li item])]])



(defn dropdown-panel
  "Panel with open/closed state that shows content only when open.
Opts can have the following keys:
   :open   an optional atom with boolean value for open/closed state, defaults to (atom false)
   :style  a style keyword :default, :primary, :success, :info :warning, :danger
  "
  [opts title content]
  (let [open (or (:open opts) (atom false))
        style (or (:style opts) :default)]
    (fn [opts title content]
      [:div.panel {:class (str "panel-" (name style))}

       ;; Panel heading with title and clickable open/close toggle
       [:div.panel-heading {:on-click #(swap! open not)}
        [:h3.panel-title title]
        [:span.pull-right.clickable
         [:i.glyphicon {:class (if @open
                                 "glyphicon-minus"
                                 "glyphicon-plus")}]]]

       ;; Panel content
       (when @open
         [:div.panel-body
          content])])))

(defn panel
  ([options content] (panel options nil content))
  ([options title content]
   [:div.panel {:class (str
                         (:class options) " panel-default")}
    (when title
      [:div.panel-heading
       [:h1.musta title]])
    [:div.panel-body
     content]]))
