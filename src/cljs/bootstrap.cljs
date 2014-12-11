(ns bootstrap
;;  "Common Bootstrap components for Reagent UI."
  (:require [reagent.core :refer [atom]]))


(defn tabs
  "A tabbed panel. Takes a map of configuration parameters and alternating tab titles and tab components.
The following keys are supported in the configuration:

  :active    An atom containing the selected tab number. Defaults to (atom 0).
  :style     Tab style, either :pills or :tabs. Defaults to :tabs. "
  
  [config & alternating-title-and-component]
  (let [active (or (:active config) (atom 0))
        style-class (case (or (:style config) :tabs)
                      :pills "nav-pills"
                      :tabs "nav-tabs")
        tabs (partition 2 alternating-title-and-component)]
    (fn []
      (let [[active-tab-title active-component] (nth tabs @active)]
        [:span 
         [:ul.nav {:class style-class}
          (map-indexed 
           (fn [i [title]]
             [:li {:role "presentation" 
                   :class (when (= active-tab-title title)
                            "active")}
              [:a {:href "#" :on-click #(reset! active i)}
               title]])
           tabs)]
         active-component]))))
  
(defn navbar
  "A Bootstrap navbar component"
  [options header & items]
  (let [collapse-state (atom "collapse")]
    (fn []
      [:nav.navbar.navbar-default {:role "navigation"}
       [:div.container-fluid
        
        ;; Brand and toggle get grouped for better mobile display 
        [:div.navbar-header
         [:button.navbar-toggle.collapsed {:type "button"} ;; toggle collapse:  data-toggle="collapse" data-target="#bs-example-navbar-collapse-1">
          [:span.sr-only "Toggle navigation"]
          [:span.icon-bar]
          [:span.icon-bar]
          [:span.icon-bar]]
         [:a.navbar-brand {:href "#"} header]]
        
        ;; Collect the nav links, forms, and other content for toggling
        [:div.navbar-collapse {:class @collapse-state}
         [:ul.nav.navbar-nav
          (for [item items]
            ;;<li class="active"><a href="#">Link <span class="sr-only">(current)</span></a></li>
            [:li {:class (str (when false "active")
                              " "
                              (:context (meta (first item))))} ;; context meta is for adapting parent container depending on child type
             item])]]]])))

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

(defn ^{:splice-in-container-class "form"}
  form
  "A bootstrap form, parent container should splice in a context class."
  [items]
  [:form {:role "search"}
   ])
;;
;;        
;;        <li><a href="#">Link</a></li>
;;        <li class="dropdown">
;;          <a href="#" class="dropdown-toggle" data-toggle="dropdown" role="button" aria-expanded="false">Dropdown <span class="caret"></span></a>
;;          <ul class="dropdown-menu" role="menu">
;;            <li><a href="#">Action</a></li>
;;            <li><a href="#">Another action</a></li>
;;            <li><a href="#">Something else here</a></li>
;;            <li class="divider"></li>
;;            <li><a href="#">Separated link</a></li>
;;            <li class="divider"></li>
;;            <li><a href="#">One more separated link</a></li>
;;          </ul>
;;        </li>
;;      </ul>
;;      <form class="navbar-form navbar-left" role="search">
;;        <div class="form-group">
;;          <input type="text" class="form-control" placeholder="Search">
;;        </div>
;;        <button type="submit" class="btn btn-default">Submit</button>
;;      </form>
;;      <ul class="nav navbar-nav navbar-right">
;;        <li><a href="#">Link</a></li>
;;        <li class="dropdown">
;;          <a href="#" class="dropdown-toggle" data-toggle="dropdown" role="button" aria-expanded="false">Dropdown <span class="caret"></span></a>
;;          <ul class="dropdown-menu" role="menu">
;;            <li><a href="#">Action</a></li>
;;            <li><a href="#">Another action</a></li>
;;            <li><a href="#">Something else here</a></li>
;;            <li class="divider"></li>
;;            <li><a href="#">Separated link</a></li>
;;          </ul>
;;        </li>
;;      </ul>
;;    </div><!-- /.navbar-collapse -->
;;  </div><!-- /.container-fluid -->
;;</nav>
