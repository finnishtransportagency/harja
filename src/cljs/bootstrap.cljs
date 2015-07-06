(ns bootstrap
  "Common Bootstrap components for Reagent UI."
  (:require [reagent.core :refer [atom]]))


(defn tabs
  "A tabbed panel. Takes a map of configuration parameters and alternating tab titles and tab components.
The following keys are supported in the configuration:

  :active    An atom containing the selected tab number. Defaults to (atom 0).
  :style     Tab style, either :pills or :tabs. Defaults to :tabs. "

  [config & alternating-title-and-component]
  (let [active (or (:active config) (atom nil))
        style-class (case (or (:style config) :tabs)
                      :pills "nav-pills"
                      :tabs "nav-tabs")]
    (fn [config & alternating-title-and-component]
      (let [tabs (filter #(not (nil? (nth % 2))) (partition 3 alternating-title-and-component))
            [active-tab-title active-tab-keyword active-component] (first (filter #(= @active (nth % 1)) tabs))]
        [:span
         [:ul.nav {:class style-class}
          (map
            (fn [[title keyword]]
              ^{:key title}
              [:li {:role  "presentation"
                    :class (when (= keyword active-tab-keyword)
                             "active")}
               [:a.klikattava {:on-click #(do
                                           (.preventDefault %)
                                           (reset! active keyword))}
                title]])
            tabs)]
         [:div.valilehti active-component]]))))

(defn navbar
  "A Bootstrap navbar component"
  [options header & items]
  (let [collapse-state (atom "collapse")]
    (fn [options header & items]
      [:nav.navbar.navbar-default {:role "navigation"}
       [:div.container-fluid

        ;; Brand and toggle get grouped for better mobile display 
        [:div.navbar-header
         [:button.navbar-toggle.collapsed {:type "button"}  ;; toggle collapse:  data-toggle="collapse" data-target="#bs-example-navbar-collapse-1">
          [:span.sr-only "Toggle navigation"]
          [:span.icon-bar]
          [:span.icon-bar]
          [:span.icon-bar]]
         [:a.navbar-brand {:href "#"} header]]

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
   [:div.panel {:class (or
                         (:class options)
                         (case (or (:style options) :default)
                           :default "panel-default"))}
    (when title
      [:div.panel-heading
       [:h3.panel-title title]])
    [:div.panel-body
     content]]))


