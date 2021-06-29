(ns harja.ui.ikonit
  "Kaikki bootstrap ja muut ikonit."
  (:refer-clojure :exclude [remove repeat print list filter sort])
  (:require-macros [harja.ui.svg-sprite :refer [maarittele-svg-spritet]]))

;; Bootstrap ikonit
(defn euro []
  [:span.glyphicon.glyphicon-euro])
(defn eur []
  [:span.glyphicon.glyphicon-eur])
(defn minus []
  [:span.glyphicon.glyphicon-minus])
(defn envelope []
  [:span.glyphicon.glyphicon-envelope])
(defn pencil []
  [:span.glyphicon.glyphicon-pencil])
(defn glass []
  [:span.glyphicon.glyphicon-glass])
(defn music []
  [:span.glyphicon.glyphicon-music])
(defn star []
  [:span.glyphicon.glyphicon-star])
(defn star-empty []
  [:span.glyphicon.glyphicon-star-empty])
(defn user []
  [:span.glyphicon.glyphicon-user])
(defn film []
  [:span.glyphicon.glyphicon-film])
(defn th-large []
  [:span.glyphicon.glyphicon-th-large])
(defn th []
  [:span.glyphicon.glyphicon-th])
(defn th-list []
  [:span.glyphicon.glyphicon-th-list])
(defn ok []
  [:span.glyphicon.glyphicon-ok])
(defn remove []
  [:span.glyphicon.glyphicon-remove])
(defn zoom-in []
  [:span.glyphicon.glyphicon-zoom-in])
(defn off []
  [:span.glyphicon.glyphicon-off])
(defn signal []
  [:span.glyphicon.glyphicon-signal])
(defn file []
  [:span.glyphicon.glyphicon-file])
(defn aika [] ;; aika koska clj coressa on time myös
  [:span.glyphicon.glyphicon-time])
(defn road []
  [:span.glyphicon.glyphicon-road])
(defn download-alt []
  [:span.glyphicon.glyphicon-download-alt])
(defn inbox []
  [:span.glyphicon.glyphicon-inbox])
(defn repeat []
  [:span.glyphicon.glyphicon-repeat])
(defn refresh []
  [:span.glyphicon.glyphicon-refresh])
(defn list-alt []
  [:span.glyphicon.glyphicon-list-alt])
(defn lock []
  [:span.glyphicon.glyphicon-lock])
(defn flag []
  [:span.glyphicon.glyphicon-flag])
(defn volume-down []
  [:span.glyphicon.glyphicon-volume-down])
(defn tag []
  [:span.glyphicon.glyphicon-tag])
(defn tags []
  [:span.glyphicon.glyphicon-tags])
(defn book []
  [:span.glyphicon.glyphicon-book])
(defn print []
  [:span.glyphicon.glyphicon-print])
(defn font []
  [:span.glyphicon.glyphicon-font])
(defn bold []
  [:span.glyphicon.glyphicon-bold])
(defn italic []
  [:span.glyphicon.glyphicon-italic])
(defn text-height []
  [:span.glyphicon.glyphicon-text-height])
(defn text-width []
  [:span.glyphicon.glyphicon-text-width])
(defn align-left []
  [:span.glyphicon.glyphicon-align-left])
(defn align-center []
  [:span.glyphicon.glyphicon-align-center])
(defn align-right []
  [:span.glyphicon.glyphicon-align-right])
(defn align-justify []
  [:span.glyphicon.glyphicon-align-justify])
(defn list []
  [:span.glyphicon.glyphicon-list])
(defn indent-left []
  [:span.glyphicon.glyphicon-indent-left])
(defn indent-right []
  [:span.glyphicon.glyphicon-indent-right])
(defn facetime-video []
  [:span.glyphicon.glyphicon-facetime-video])
(defn picture []
  [:span.glyphicon.glyphicon-picture])
(defn map-marker []
  [:span.glyphicon.glyphicon-map-marker])
(defn adjust []
  [:span.glyphicon.glyphicon-adjust])
(defn tint []
  [:span.glyphicon.glyphicon-tint])
(defn edit []
  [:span.glyphicon.glyphicon-edit])
(defn share []
  [:span.glyphicon.glyphicon-share])
(defn check []
  [:span.glyphicon.glyphicon-check])
(defn move []
  [:span.glyphicon.glyphicon-move])
(defn play []
  [:span.glyphicon.glyphicon-play])
(defn pause []
  [:span.glyphicon.glyphicon-pause])
(defn stop []
  [:span.glyphicon.glyphicon-stop])
(defn forward []
  [:span.glyphicon.glyphicon-forward])
(defn eject []
  [:span.glyphicon.glyphicon-eject])
(defn plus-sign []
  [:span.glyphicon.glyphicon-plus-sign])
(defn remove-sign []
  [:span.glyphicon.glyphicon-remove-sign])
(defn ok-sign []
  [:span.glyphicon.glyphicon-ok-sign])
(defn screenshot []
  [:span.glyphicon.glyphicon-screenshot])
(defn remove-circle []
  [:span.glyphicon.glyphicon-remove-circle])
(defn ok-circle []
  [:span.glyphicon.glyphicon-ok-circle])
(defn ban-circle []
  [:span.glyphicon.glyphicon-ban-circle])
(defn resize-small []
  [:span.glyphicon.glyphicon-resize-small])
(defn exclamation-sign []
  [:span.glyphicon.glyphicon-exclamation-sign])
(defn fire []
  [:span.glyphicon.glyphicon-fire])
(defn warning []
  [:span.glyphicon.glyphicon-warning-sign])
(defn eye-open []
  [:span.glyphicon.glyphicon-eye-open])
(defn plane []
  [:span.glyphicon.glyphicon-plane])
(defn calendar []
  [:span.glyphicon.glyphicon-calendar])
(defn random []
  [:span.glyphicon.glyphicon-random])
(defn retweet []
  [:span.glyphicon.glyphicon-retweet])
(defn resize-vertical []
  [:span.glyphicon.glyphicon-resize-vertical])
(defn hdd []
  [:span.glyphicon.glyphicon-hdd])
(defn certificate []
  [:span.glyphicon.glyphicon-certificate])
(defn thumbs-up []
  [:span.glyphicon.glyphicon-thumbs-up])
(defn thumbs-down []
  [:span.glyphicon.glyphicon-thumbs-down])
(defn circle-arrow-right []
  [:span.glyphicon.glyphicon-circle-arrow-right])
(defn circle-arrow-left []
  [:span.glyphicon.glyphicon-circle-arrow-left])
(defn tasks []
  [:span.glyphicon.glyphicon-tasks])
(defn filter []
  [:span.glyphicon.glyphicon-filter])
(defn link []
  [:span.glyphicon.glyphicon-link])
(defn phone []
  [:span.glyphicon.glyphicon-phone])
(defn usd []
  [:span.glyphicon.glyphicon-usd])
(defn sort []
  [:span.glyphicon.glyphicon-sort])
(defn sort-by-order []
  [:span.glyphicon.glyphicon-sort-by-order])
(defn unchecked []
  [:span.glyphicon.glyphicon-unchecked])
(defn expand []
  [:span.glyphicon.livicon-expand])
(defn log-in []
  [:span.glyphicon.glyphicon-log-in])
(defn flash []
  [:span.glyphicon.glyphicon-flash])
(defn log-out []
  [:span.glyphicon.glyphicon-log-out])
(defn new-window []
  [:span.glyphicon.glyphicon-new-window])
(defn record []
  [:span.glyphicon.glyphicon-record])
(defn save []
  [:span.glyphicon.glyphicon-save])
(defn open []
  [:span.glyphicon.glyphicon-open])
(defn export []
  [:span.glyphicon.glyphicon-export])
(defn send []
  [:span.glyphicon.glyphicon-send])
(defn header []
  [:span.glyphicon.glyphicon-header])

(defn compress []
  [:span.glyphicon.livicon-compress])
(defn stats []
  [:span.glyphicon.glyphicon-stats])
(defn sound-5-1 []
  [:span.glyphicon.glyphicon-sound-5-1])
(defn sound-6-1 []
  [:span.glyphicon.glyphicon-sound-6-1])
(defn sound-7-1 []
  [:span.glyphicon.glyphicon-sound-7-1])
(defn cd []
  [:span.glyphicon.glyphicon-cd])
(defn open-file []
  [:span.glyphicon.glyphicon-open-file])
(defn level-up []
  [:span.glyphicon.glyphicon-level-up])
(defn copy []
  [:span.glyphicon.glyphicon-copy])
(defn paste []
  [:span.glyphicon.glyphicon-paste])
(defn alert []
  [:span.glyphicon.glyphicon-alert])
(defn pawn []
  [:span.glyphicon.glyphicon-pawn])
(defn tent []
  [:span.glyphicon.glyphicon-tent])
(defn bed []
  [:span.glyphicon.glyphicon-bed])
(defn apple []
  [:span.glyphicon.glyphicon-apple])
(defn erase []
  [:span.glyphicon.glyphicon-erase])
(defn lamp []
  [:span.glyphicon.glyphicon-lamp])
(defn ruble []
  [:span.glyphicon.glyphicon-ruble])
(defn scale []
  [:span.glyphicon.glyphicon-scale])
(defn option-vertical []
  [:span.glyphicon.glyphicon-option-vertical])
(defn modal-window []
  [:span.glyphicon.glyphicon-modal-window])
(defn text-size []
  [:span.glyphicon.glyphicon-text-size])
(defn text-color []
  [:span.glyphicon.glyphicon-text-color])
(defn text-background []
  [:span.glyphicon.glyphicon-text-background])
(defn triangle-right []
  [:span.glyphicon.glyphicon-triangle-right])
(defn triangle-left []
  [:span.glyphicon.glyphicon-triangle-left])
(defn triangle-bottom []
  [:span.glyphicon.glyphicon-triangle-bottom])
(defn triangle-top []
  [:span.glyphicon.glyphicon-triangle-top])
(defn console []
  [:span.glyphicon.glyphicon-console])
(defn menu-left []
  [:span.glyphicon.glyphicon-menu-left])
(defn menu-right []
  [:span.glyphicon.glyphicon-menu-right])
(defn menu-down []
  [:span.glyphicon.glyphicon-menu-down])
(defn menu-up []
  [:span.glyphicon.glyphicon-menu-up])

;; Open iconi

(defn oi-caret-bottom []
  [:img {:src "open-iconic/svg/caret-bottom.svg" :alt "caret-bottom"}])
(defn oi-caret-left []
  [:img {:src "open-iconic/svg/caret-left.svg" :alt "caret-left"}])
(defn oi-caret-right []
  [:img {:src "open-iconic/svg/caret-right.svg" :alt "caret-right"}])
(defn oi-caret-top []
  [:img {:src "open-iconic/svg/caret-top.svg" :alt "caret-top"}])
(defn oi-x []
  [:img {:src "open-iconic/svg/x.svg" :alt "x"}])
(defn oi-trash []
  [:img {:src "open-iconic/svg/trash.svg" :alt "trash"}])

;; Uudet Väyläikonit
(defn close-svg []
  [:img {:src "images/harja-icons/navigation/close.svg" :alt "close"}])

(defn copy-lane-svg []
  [:img {:src "images/harja-icons/action/copy-lane.svg" :alt "copy-lane"}])

;; Livin ikonit

(defn livicon-ban []
  [:span.livicon-ban])
(defn livicon-chevron-up []
  [:span.livicon-chevron.livicon-chevron-up])
(defn livicon-chevron-right []
  [:span.livicon-chevron.livicon-chevron-right])
(defn livicon-chevron-down []
  [:span.livicon-chevron.livicon-chevron-down])
(defn livicon-chevron-left []
  [:span.livicon-chevron.livicon-chevron-left])
(defn livicon-circle []
  [:span.livicon-circle])
(defn livicon-download []
  [:span.livicon-download])
(defn livicon-external []
  [:span.livicon-external])
(defn livicon-info-sign []
  [:span.livicon-info-circle])
(defn livicon-info []
  [:span.livicon-info])
(defn livicon-info-circle []
  [:span.livicon-info-circle])
(defn livicon-question []
  [:span.livicon-question])
(defn livicon-question-circle []
  [:span.livicon-question-circle])
(defn livicon-search []
  [:span.livicon-search])
(defn livicon-save []
  [:span.livicon-save])
(defn livicon-plus []
  [:span.livicon-plus])
(defn livicon-minus []
  [:span.livicon-minus])
(defn livicon-pen []
  [:span.livicon-pen])
(defn livicon-duplicate []
  [:span.livicon-duplicate])
(defn livicon-warning-sign []
  [:span.livicon-exclamation-triangle])
(defn livicon-trash []
  [:span.livicon-trash])
(defn livicon-trash-disabled [tooltip]
  [:span.livicon-trash {:style {:opacity "0.3"} :title tooltip}])
(defn livicon-upload []
  [:span.livicon-upload])
(defn livicon-arrow-left []
  [:span.livicon-arrow-left])
(defn livicon-arrow-right []
  [:span.livicon-arrow-right])
(defn livicon-arrow-up []
  [:span.livicon-arrow-up])
(defn livicon-arrow-down []
  [:span.livicon-arrow-down])
(defn livicon-arrow-bottom []
  [:span.livicon-arrow-bottom])
(defn livicon-arrow-top []
  [:span.livicon-arrow-top])
(defn livicon-check []
  [:span.livicon-check])
(defn livicon-wrench []
  [:span.livicon-wrench])
(defn livicon-exclamation []
  [:span.livicon-exclamation])
(defn livicon-eye []
  [:span.livicon-eye])
(defn livicon-delete []
  [:span.livicon-delete])
(defn livicon-square []
  [:div.livicon-square])
(defn livicon-document-full []
  [:span.livicon-document-full])

;; Tiettyjen toimintojen vakioikonit

(defn muokkaa []
  [:span.livicon-pen])
(defn kumoa []
  [:span.livicon-rotate-left])
(defn peru []
  [:span.livicon-rotate-left])
(defn sulje []
  [:span.livicon-delete])
(defn tee-sittenkin []
  [:span.livicon-rotate-right])
(defn tallenna []
  [:span.livicon-check])
(defn livicon-kommentti []
  [:span.livicon-comment])
(defn livicon-back-circle []
  [:img {:src "images/harja-icons/action/back-circle.svg" :alt "peruuta" :style {:padding-bottom "1px"}}])

(defn klikattava-roskis [toiminto]
  [:span.klikattava
   {:on-click toiminto}
   (livicon-trash)])

(defn action-add []
  [:img {:src "images/harja-icons/action/add.svg" :alt "add"}])
(defn action-copy []
  [:img {:src "images/harja-icons/action/copy.svg" :alt "copy"}])
(defn action-edit []
  [:img {:src "images/harja-icons/action/edit.svg" :alt "edit"}])
(defn action-delete []
  [:img {:src "images/harja-icons/action/delete.svg" :alt "delete"}])
(defn action-sort-descending []
  [:img {:src "images/harja-icons/action/sort-descending.svg" :alt "descending sort"}])
(defn nelio-info []
  [:img {:src "images/harja-icons/status/info.svg" :alt "info"}])

(defn status-info-inline-svg
  [color]
  [:svg {:width "24px" :height "24px" :viewBox "0 0 24 24" :fill "none" :xmlns "http://www.w3.org/2000/svg"}
   [:path {:d "M24 24H0V0H24V24ZM2 22H22V2H2V22ZM12 4.5C11.2 4.5 10.5 5.2 10.5 6C10.5 6.8 11.2 7.5 12 7.5C12.8 7.5 13.5 6.8 13.5 6C13.5 5.2 12.8 4.5 12 4.5ZM13 10H11V20H13V10Z"
           :fill (or color "#004D99")}]])

(defn navigation-up []
  [:img.navigation-up {:src "images/harja-icons/navigation/up.svg" :alt "up"}])
(defn navigation-right []
  [:img.navigation-right {:src "images/harja-icons/navigation/right.svg" :alt "right"}])
(defn navigation-down []
  [:img.navigation-down {:src "images/harja-icons/navigation/down.svg" :alt "down"}])
(defn navigation-left []
  [:img.navigation-left {:src "images/harja-icons/navigation/left.svg" :alt "left"}])

(defn navigation-ympyrassa [suunta]
  [:div.navigation-ympyrassa
   (case suunta
     :up (navigation-up)
     :right (navigation-right)
     :down (navigation-down)
     :left (navigation-left)

     nil)])

(defn ikoni-ja-teksti [ikoni teksti]
  [:span
   ikoni
   [:span (str " " teksti)]])

(defn ikoni-ja-elementti [ikoni elementti]
  [:span
   ikoni
   [:span " "]
   elementti])

(defn teksti-ja-ikoni [teksti ikoni]
  [:span
   [:span (str teksti " ")]
   ikoni])

(maarittele-svg-spritet 24 24 "livicons-24.svg")

(defn sulje-ruksi
  ([sulje!]
   [sulje-ruksi sulje! {}])
  ([sulje! {:keys [style]}]
   [:button.close {:on-click sulje!
                   :style (merge
                            {:color "black"
                             :margin "15px"
                             :opacity 1}
                            style)
                   :type "button"}
    [close-svg]]))