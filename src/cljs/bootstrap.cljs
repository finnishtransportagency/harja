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
             ^{:key title}
             [:li {:role "presentation" 
                   :class (when (= active-tab-title title)
                            "active")}
              [:a {:href "#" :on-click #(reset! active i)}
               title]])
           tabs)]
         [:div.valilehti active-component]]))))
  
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
        (let [[left-items _ right-items] (partition-by #(= :right %) items)] 
          [:div.navbar-collapse {:class @collapse-state}
           (when left-items
             [:ul.nav.navbar-nav
              (for [item left-items]
                ;;<li class="active"><a href="#">Link <span class="sr-only">(current)</span></a></li>
                [:li {:class (str (when false "active")
                                  " "
                                  (:context (meta (first item))))} ;; context meta is for adapting parent container depending on child type
                 item])])
           (when right-items
             [:ul.nav.navbar-nav.navbar-right
              (for [item right-items]
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
    (fn []
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
     [:div.panel {:class (case (or (:style options) :default)
                           :default "panel-default")}
      (when title
        [:div.panel-heading
         [:h3.panel-title title]])
      [:div.panel-body
       content]]))


;; Määritellään bootstrap ikonit

(defn icon-asterisk []
  [:span.glyphicon.glyphicon-asterisk])
(defn icon-plus []
  [:span.glyphicon.glyphicon-plus])
(defn icon-euro []
  [:span.glyphicon.glyphicon-euro])
(defn icon-eur []
  [:span.glyphicon.glyphicon-eur])
(defn icon-minus []
  [:span.glyphicon.glyphicon-minus])
(defn icon-cloud []
  [:span.glyphicon.glyphicon-cloud])
(defn icon-envelope []
  [:span.glyphicon.glyphicon-envelope])
(defn icon-pencil []
  [:span.glyphicon.glyphicon-pencil])
(defn icon-glass []
  [:span.glyphicon.glyphicon-glass])
(defn icon-music []
  [:span.glyphicon.glyphicon-music])
(defn icon-search []
  [:span.glyphicon.glyphicon-search])
(defn icon-heart []
  [:span.glyphicon.glyphicon-heart])
(defn icon-star []
  [:span.glyphicon.glyphicon-star])
(defn icon-star-empty []
  [:span.glyphicon.glyphicon-star-empty])
(defn icon-user []
  [:span.glyphicon.glyphicon-user])
(defn icon-film []
  [:span.glyphicon.glyphicon-film])
(defn icon-th-large []
  [:span.glyphicon.glyphicon-th-large])
(defn icon-th []
  [:span.glyphicon.glyphicon-th])
(defn icon-th-list []
  [:span.glyphicon.glyphicon-th-list])
(defn icon-ok []
  [:span.glyphicon.glyphicon-ok])
(defn icon-remove []
  [:span.glyphicon.glyphicon-remove])
(defn icon-zoom-in []
  [:span.glyphicon.glyphicon-zoom-in])
(defn icon-zoom-out []
  [:span.glyphicon.glyphicon-zoom-out])
(defn icon-off []
  [:span.glyphicon.glyphicon-off])
(defn icon-signal []
  [:span.glyphicon.glyphicon-signal])
(defn icon-cog []
  [:span.glyphicon.glyphicon-cog])
(defn icon-trash []
  [:span.glyphicon.glyphicon-trash])
(defn icon-home []
  [:span.glyphicon.glyphicon-home])
(defn icon-file []
  [:span.glyphicon.glyphicon-file])
(defn icon-time []
  [:span.glyphicon.glyphicon-time])
(defn icon-road []
  [:span.glyphicon.glyphicon-road])
(defn icon-download-alt []
  [:span.glyphicon.glyphicon-download-alt])
(defn icon-download []
  [:span.glyphicon.glyphicon-download])
(defn icon-upload []
  [:span.glyphicon.glyphicon-upload])
(defn icon-inbox []
  [:span.glyphicon.glyphicon-inbox])
(defn icon-play-circle []
  [:span.glyphicon.glyphicon-play-circle])
(defn icon-repeat []
  [:span.glyphicon.glyphicon-repeat])
(defn icon-refresh []
  [:span.glyphicon.glyphicon-refresh])
(defn icon-list-alt []
  [:span.glyphicon.glyphicon-list-alt])
(defn icon-lock []
  [:span.glyphicon.glyphicon-lock])
(defn icon-flag []
  [:span.glyphicon.glyphicon-flag])
(defn icon-headphones []
  [:span.glyphicon.glyphicon-headphones])
(defn icon-volume-off []
  [:span.glyphicon.glyphicon-volume-off])
(defn icon-volume-down []
  [:span.glyphicon.glyphicon-volume-down])
(defn icon-volume-up []
  [:span.glyphicon.glyphicon-volume-up])
(defn icon-qrcode []
  [:span.glyphicon.glyphicon-qrcode])
(defn icon-barcode []
  [:span.glyphicon.glyphicon-barcode])
(defn icon-tag []
  [:span.glyphicon.glyphicon-tag])
(defn icon-tags []
  [:span.glyphicon.glyphicon-tags])
(defn icon-book []
  [:span.glyphicon.glyphicon-book])
(defn icon-bookmark []
  [:span.glyphicon.glyphicon-bookmark])
(defn icon-print []
  [:span.glyphicon.glyphicon-print])
(defn icon-camera []
  [:span.glyphicon.glyphicon-camera])
(defn icon-font []
  [:span.glyphicon.glyphicon-font])
(defn icon-bold []
  [:span.glyphicon.glyphicon-bold])
(defn icon-italic []
  [:span.glyphicon.glyphicon-italic])
(defn icon-text-height []
  [:span.glyphicon.glyphicon-text-height])
(defn icon-text-width []
  [:span.glyphicon.glyphicon-text-width])
(defn icon-align-left []
  [:span.glyphicon.glyphicon-align-left])
(defn icon-align-center []
  [:span.glyphicon.glyphicon-align-center])
(defn icon-align-right []
  [:span.glyphicon.glyphicon-align-right])
(defn icon-align-justify []
  [:span.glyphicon.glyphicon-align-justify])
(defn icon-list []
  [:span.glyphicon.glyphicon-list])
(defn icon-indent-left []
  [:span.glyphicon.glyphicon-indent-left])
(defn icon-indent-right []
  [:span.glyphicon.glyphicon-indent-right])
(defn icon-facetime-video []
  [:span.glyphicon.glyphicon-facetime-video])
(defn icon-picture []
  [:span.glyphicon.glyphicon-picture])
(defn icon-map-marker []
  [:span.glyphicon.glyphicon-map-marker])
(defn icon-adjust []
  [:span.glyphicon.glyphicon-adjust])
(defn icon-tint []
  [:span.glyphicon.glyphicon-tint])
(defn icon-edit []
  [:span.glyphicon.glyphicon-edit])
(defn icon-share []
  [:span.glyphicon.glyphicon-share])
(defn icon-check []
  [:span.glyphicon.glyphicon-check])
(defn icon-move []
  [:span.glyphicon.glyphicon-move])
(defn icon-step-backward []
  [:span.glyphicon.glyphicon-step-backward])
(defn icon-fast-backward []
  [:span.glyphicon.glyphicon-fast-backward])
(defn icon-backward []
  [:span.glyphicon.glyphicon-backward])
(defn icon-play []
  [:span.glyphicon.glyphicon-play])
(defn icon-pause []
  [:span.glyphicon.glyphicon-pause])
(defn icon-stop []
  [:span.glyphicon.glyphicon-stop])
(defn icon-forward []
  [:span.glyphicon.glyphicon-forward])
(defn icon-fast-forward []
  [:span.glyphicon.glyphicon-fast-forward])
(defn icon-step-forward []
  [:span.glyphicon.glyphicon-step-forward])
(defn icon-eject []
  [:span.glyphicon.glyphicon-eject])
(defn icon-chevron-left []
  [:span.glyphicon.glyphicon-chevron-left])
(defn icon-chevron-right []
  [:span.glyphicon.glyphicon-chevron-right])
(defn icon-plus-sign []
  [:span.glyphicon.glyphicon-plus-sign])
(defn icon-minus-sign []
  [:span.glyphicon.glyphicon-minus-sign])
(defn icon-remove-sign []
  [:span.glyphicon.glyphicon-remove-sign])
(defn icon-ok-sign []
  [:span.glyphicon.glyphicon-ok-sign])
(defn icon-question-sign []
  [:span.glyphicon.glyphicon-question-sign])
(defn icon-info-sign []
  [:span.glyphicon.glyphicon-info-sign])
(defn icon-screenshot []
  [:span.glyphicon.glyphicon-screenshot])
(defn icon-remove-circle []
  [:span.glyphicon.glyphicon-remove-circle])
(defn icon-ok-circle []
  [:span.glyphicon.glyphicon-ok-circle])
(defn icon-ban-circle []
  [:span.glyphicon.glyphicon-ban-circle])
(defn icon-arrow-left []
  [:span.glyphicon.glyphicon-arrow-left])
(defn icon-arrow-right []
  [:span.glyphicon.glyphicon-arrow-right])
(defn icon-arrow-up []
  [:span.glyphicon.glyphicon-arrow-up])
(defn icon-arrow-down []
  [:span.glyphicon.glyphicon-arrow-down])
(defn icon-share-alt []
  [:span.glyphicon.glyphicon-share-alt])
(defn icon-resize-full []
  [:span.glyphicon.glyphicon-resize-full])
(defn icon-resize-small []
  [:span.glyphicon.glyphicon-resize-small])
(defn icon-exclamation-sign []
  [:span.glyphicon.glyphicon-exclamation-sign])
(defn icon-gift []
  [:span.glyphicon.glyphicon-gift])
(defn icon-leaf []
  [:span.glyphicon.glyphicon-leaf])
(defn icon-fire []
  [:span.glyphicon.glyphicon-fire])
(defn icon-eye-open []
  [:span.glyphicon.glyphicon-eye-open])
(defn icon-eye-close []
  [:span.glyphicon.glyphicon-eye-close])
(defn icon-warning-sign []
  [:span.glyphicon.glyphicon-warning-sign])
(defn icon-plane []
  [:span.glyphicon.glyphicon-plane])
(defn icon-calendar []
  [:span.glyphicon.glyphicon-calendar])
(defn icon-random []
  [:span.glyphicon.glyphicon-random])
(defn icon-comment []
  [:span.glyphicon.glyphicon-comment])
(defn icon-magnet []
  [:span.glyphicon.glyphicon-magnet])
(defn icon-chevron-up []
  [:span.glyphicon.glyphicon-chevron-up])
(defn icon-chevron-down []
  [:span.glyphicon.glyphicon-chevron-down])
(defn icon-retweet []
  [:span.glyphicon.glyphicon-retweet])
(defn icon-shopping-cart []
  [:span.glyphicon.glyphicon-shopping-cart])
(defn icon-folder-close []
  [:span.glyphicon.glyphicon-folder-close])
(defn icon-folder-open []
  [:span.glyphicon.glyphicon-folder-open])
(defn icon-resize-vertical []
  [:span.glyphicon.glyphicon-resize-vertical])
(defn icon-resize-horizontal []
  [:span.glyphicon.glyphicon-resize-horizontal])
(defn icon-hdd []
  [:span.glyphicon.glyphicon-hdd])
(defn icon-bullhorn []
  [:span.glyphicon.glyphicon-bullhorn])
(defn icon-bell []
  [:span.glyphicon.glyphicon-bell])
(defn icon-certificate []
  [:span.glyphicon.glyphicon-certificate])
(defn icon-thumbs-up []
  [:span.glyphicon.glyphicon-thumbs-up])
(defn icon-thumbs-down []
  [:span.glyphicon.glyphicon-thumbs-down])
(defn icon-hand-right []
  [:span.glyphicon.glyphicon-hand-right])
(defn icon-hand-left []
  [:span.glyphicon.glyphicon-hand-left])
(defn icon-hand-up []
  [:span.glyphicon.glyphicon-hand-up])
(defn icon-hand-down []
  [:span.glyphicon.glyphicon-hand-down])
(defn icon-circle-arrow-right []
  [:span.glyphicon.glyphicon-circle-arrow-right])
(defn icon-circle-arrow-left []
  [:span.glyphicon.glyphicon-circle-arrow-left])
(defn icon-circle-arrow-up []
  [:span.glyphicon.glyphicon-circle-arrow-up])
(defn icon-circle-arrow-down []
  [:span.glyphicon.glyphicon-circle-arrow-down])
(defn icon-globe []
  [:span.glyphicon.glyphicon-globe])
(defn icon-wrench []
  [:span.glyphicon.glyphicon-wrench])
(defn icon-tasks []
  [:span.glyphicon.glyphicon-tasks])
(defn icon-filter []
  [:span.glyphicon.glyphicon-filter])
(defn icon-briefcase []
  [:span.glyphicon.glyphicon-briefcase])
(defn icon-fullscreen []
  [:span.glyphicon.glyphicon-fullscreen])
(defn icon-dashboard []
  [:span.glyphicon.glyphicon-dashboard])
(defn icon-paperclip []
  [:span.glyphicon.glyphicon-paperclip])
(defn icon-heart-empty []
  [:span.glyphicon.glyphicon-heart-empty])
(defn icon-link []
  [:span.glyphicon.glyphicon-link])
(defn icon-phone []
  [:span.glyphicon.glyphicon-phone])
(defn icon-pushpin []
  [:span.glyphicon.glyphicon-pushpin])
(defn icon-usd []
  [:span.glyphicon.glyphicon-usd])
(defn icon-gbp []
  [:span.glyphicon.glyphicon-gbp])
(defn icon-sort []
  [:span.glyphicon.glyphicon-sort])
(defn icon-sort-by-alphabet []
  [:span.glyphicon.glyphicon-sort-by-alphabet])
(defn icon-sort-by-alphabet-alt []
  [:span.glyphicon.glyphicon-sort-by-alphabet-alt])
(defn icon-sort-by-order []
  [:span.glyphicon.glyphicon-sort-by-order])
(defn icon-sort-by-order-alt []
  [:span.glyphicon.glyphicon-sort-by-order-alt])
(defn icon-sort-by-attributes []
  [:span.glyphicon.glyphicon-sort-by-attributes])
(defn icon-sort-by-attributes-alt []
  [:span.glyphicon.glyphicon-sort-by-attributes-alt])
(defn icon-unchecked []
  [:span.glyphicon.glyphicon-unchecked])
(defn icon-expand []
  [:span.glyphicon.glyphicon-expand])
(defn icon-collapse-down []
  [:span.glyphicon.glyphicon-collapse-down])
(defn icon-collapse-up []
  [:span.glyphicon.glyphicon-collapse-up])
(defn icon-log-in []
  [:span.glyphicon.glyphicon-log-in])
(defn icon-flash []
  [:span.glyphicon.glyphicon-flash])
(defn icon-log-out []
  [:span.glyphicon.glyphicon-log-out])
(defn icon-new-window []
  [:span.glyphicon.glyphicon-new-window])
(defn icon-record []
  [:span.glyphicon.glyphicon-record])
(defn icon-save []
  [:span.glyphicon.glyphicon-save])
(defn icon-open []
  [:span.glyphicon.glyphicon-open])
(defn icon-saved []
  [:span.glyphicon.glyphicon-saved])
(defn icon-import []
  [:span.glyphicon.glyphicon-import])
(defn icon-export []
  [:span.glyphicon.glyphicon-export])
(defn icon-send []
  [:span.glyphicon.glyphicon-send])
(defn icon-floppy-disk []
  [:span.glyphicon.glyphicon-floppy-disk])
(defn icon-floppy-saved []
  [:span.glyphicon.glyphicon-floppy-saved])
(defn icon-floppy-remove []
  [:span.glyphicon.glyphicon-floppy-remove])
(defn icon-floppy-save []
  [:span.glyphicon.glyphicon-floppy-save])
(defn icon-floppy-open []
  [:span.glyphicon.glyphicon-floppy-open])
(defn icon-credit-card []
  [:span.glyphicon.glyphicon-credit-card])
(defn icon-transfer []
  [:span.glyphicon.glyphicon-transfer])
(defn icon-cutlery []
  [:span.glyphicon.glyphicon-cutlery])
(defn icon-header []
  [:span.glyphicon.glyphicon-header])
(defn icon-compressed []
  [:span.glyphicon.glyphicon-compressed])
(defn icon-earphone []
  [:span.glyphicon.glyphicon-earphone])
(defn icon-phone-alt []
  [:span.glyphicon.glyphicon-phone-alt])
(defn icon-tower []
  [:span.glyphicon.glyphicon-tower])
(defn icon-stats []
  [:span.glyphicon.glyphicon-stats])
(defn icon-sd-video []
  [:span.glyphicon.glyphicon-sd-video])
(defn icon-hd-video []
  [:span.glyphicon.glyphicon-hd-video])
(defn icon-subtitles []
  [:span.glyphicon.glyphicon-subtitles])
(defn icon-sound-stereo []
  [:span.glyphicon.glyphicon-sound-stereo])
(defn icon-sound-dolby []
  [:span.glyphicon.glyphicon-sound-dolby])
(defn icon-sound-5-1 []
  [:span.glyphicon.glyphicon-sound-5-1])
(defn icon-sound-6-1 []
  [:span.glyphicon.glyphicon-sound-6-1])
(defn icon-sound-7-1 []
  [:span.glyphicon.glyphicon-sound-7-1])
(defn icon-copyright-mark []
  [:span.glyphicon.glyphicon-copyright-mark])
(defn icon-registration-mark []
  [:span.glyphicon.glyphicon-registration-mark])
(defn icon-cloud-download []
  [:span.glyphicon.glyphicon-cloud-download])
(defn icon-cloud-upload []
  [:span.glyphicon.glyphicon-cloud-upload])
(defn icon-tree-conifer []
  [:span.glyphicon.glyphicon-tree-conifer])
(defn icon-tree-deciduous []
  [:span.glyphicon.glyphicon-tree-deciduous])
(defn icon-cd []
  [:span.glyphicon.glyphicon-cd])
(defn icon-save-file []
  [:span.glyphicon.glyphicon-save-file])
(defn icon-open-file []
  [:span.glyphicon.glyphicon-open-file])
(defn icon-level-up []
  [:span.glyphicon.glyphicon-level-up])
(defn icon-copy []
  [:span.glyphicon.glyphicon-copy])
(defn icon-paste []
  [:span.glyphicon.glyphicon-paste])
(defn icon-alert []
  [:span.glyphicon.glyphicon-alert])
(defn icon-equalizer []
  [:span.glyphicon.glyphicon-equalizer])
(defn icon-king []
  [:span.glyphicon.glyphicon-king])
(defn icon-queen []
  [:span.glyphicon.glyphicon-queen])
(defn icon-pawn []
  [:span.glyphicon.glyphicon-pawn])
(defn icon-bishop []
  [:span.glyphicon.glyphicon-bishop])
(defn icon-knight []
  [:span.glyphicon.glyphicon-knight])
(defn icon-baby-formula []
  [:span.glyphicon.glyphicon-baby-formula])
(defn icon-tent []
  [:span.glyphicon.glyphicon-tent])
(defn icon-blackboard []
  [:span.glyphicon.glyphicon-blackboard])
(defn icon-bed []
  [:span.glyphicon.glyphicon-bed])
(defn icon-apple []
  [:span.glyphicon.glyphicon-apple])
(defn icon-erase []
  [:span.glyphicon.glyphicon-erase])
(defn icon-hourglass []
  [:span.glyphicon.glyphicon-hourglass])
(defn icon-lamp []
  [:span.glyphicon.glyphicon-lamp])
(defn icon-duplicate []
  [:span.glyphicon.glyphicon-duplicate])
(defn icon-piggy-bank []
  [:span.glyphicon.glyphicon-piggy-bank])
(defn icon-scissors []
  [:span.glyphicon.glyphicon-scissors])
(defn icon-bitcoin []
  [:span.glyphicon.glyphicon-bitcoin])
(defn icon-yen []
  [:span.glyphicon.glyphicon-yen])
(defn icon-ruble []
  [:span.glyphicon.glyphicon-ruble])
(defn icon-scale []
  [:span.glyphicon.glyphicon-scale])
(defn icon-ice-lolly []
  [:span.glyphicon.glyphicon-ice-lolly])
(defn icon-ice-lolly-tasted []
  [:span.glyphicon.glyphicon-ice-lolly-tasted])
(defn icon-education []
  [:span.glyphicon.glyphicon-education])
(defn icon-option-horizontal []
  [:span.glyphicon.glyphicon-option-horizontal])
(defn icon-option-vertical []
  [:span.glyphicon.glyphicon-option-vertical])
(defn icon-menu-hamburger []
  [:span.glyphicon.glyphicon-menu-hamburger])
(defn icon-modal-window []
  [:span.glyphicon.glyphicon-modal-window])
(defn icon-oil []
  [:span.glyphicon.glyphicon-oil])
(defn icon-grain []
  [:span.glyphicon.glyphicon-grain])
(defn icon-sunglasses []
  [:span.glyphicon.glyphicon-sunglasses])
(defn icon-text-size []
  [:span.glyphicon.glyphicon-text-size])
(defn icon-text-color []
  [:span.glyphicon.glyphicon-text-color])
(defn icon-text-background []
  [:span.glyphicon.glyphicon-text-background])
(defn icon-object-align-top []
  [:span.glyphicon.glyphicon-object-align-top])
(defn icon-object-align-bottom []
  [:span.glyphicon.glyphicon-object-align-bottom])
(defn icon-object-align-horizontal []
  [:span.glyphicon.glyphicon-object-align-horizontal])
(defn icon-object-align-left []
  [:span.glyphicon.glyphicon-object-align-left])
(defn icon-object-align-vertical []
  [:span.glyphicon.glyphicon-object-align-vertical])
(defn icon-object-align-right []
  [:span.glyphicon.glyphicon-object-align-right])
(defn icon-triangle-right []
  [:span.glyphicon.glyphicon-triangle-right])
(defn icon-triangle-left []
  [:span.glyphicon.glyphicon-triangle-left])
(defn icon-triangle-bottom []
  [:span.glyphicon.glyphicon-triangle-bottom])
(defn icon-triangle-top []
  [:span.glyphicon.glyphicon-triangle-top])
(defn icon-console []
  [:span.glyphicon.glyphicon-console])
(defn icon-superscript []
  [:span.glyphicon.glyphicon-superscript])
(defn icon-subscript []
  [:span.glyphicon.glyphicon-subscript])
(defn icon-menu-left []
  [:span.glyphicon.glyphicon-menu-left])
(defn icon-menu-right []
  [:span.glyphicon.glyphicon-menu-right])
(defn icon-menu-down []
  [:span.glyphicon.glyphicon-menu-down])
(defn icon-menu-up []
  [:span.glyphicon.glyphicon-menu-up])
