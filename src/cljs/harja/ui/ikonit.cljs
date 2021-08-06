(ns harja.ui.ikonit
  "Kaikki bootstrap ja muut ikonit."
  (:refer-clojure :exclude [remove repeat print list filter sort])
  (:require-macros [harja.ui.svg-sprite :refer [maarittele-svg-spritet]]))

;; Bootstrap ikonit

(defn asterisk []
  [:span.glyphicon.glyphicon-asterisk])
(defn euro []
  [:span.glyphicon.glyphicon-euro])
(defn eur []
  [:span.glyphicon.glyphicon-eur])
(defn minus []
  [:span.glyphicon.glyphicon-minus])
(defn cloud []
  [:span.glyphicon.glyphicon-cloud])
(defn envelope []
  [:span.glyphicon.glyphicon-envelope])
(defn pencil []
  [:span.glyphicon.glyphicon-pencil])
(defn glass []
  [:span.glyphicon.glyphicon-glass])
(defn music []
  [:span.glyphicon.glyphicon-music])
(defn heart []
  [:span.glyphicon.glyphicon-heart])
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
(defn zoom-out []
  [:span.glyphicon.glyphicon-zoom-out])
(defn off []
  [:span.glyphicon.glyphicon-off])
(defn signal []
  [:span.glyphicon.glyphicon-signal])
(defn cog []
  [:span.glyphicon.glyphicon-cog])
(defn home []
  [:span.glyphicon.glyphicon-home])
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
(defn play-circle []
  [:span.glyphicon.glyphicon-play-circle])
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
(defn headphones []
  [:span.glyphicon.glyphicon-headphones])
(defn volume-off []
  [:span.glyphicon.glyphicon-volume-off])
(defn volume-down []
  [:span.glyphicon.glyphicon-volume-down])
(defn volume-up []
  [:span.glyphicon.glyphicon-volume-up])
(defn qrcode []
  [:span.glyphicon.glyphicon-qrcode])
(defn barcode []
  [:span.glyphicon.glyphicon-barcode])
(defn tag []
  [:span.glyphicon.glyphicon-tag])
(defn tags []
  [:span.glyphicon.glyphicon-tags])
(defn book []
  [:span.glyphicon.glyphicon-book])
(defn bookmark []
  [:span.glyphicon.glyphicon-bookmark])
(defn print []
  [:span.glyphicon.glyphicon-print])
(defn camera []
  [:span.glyphicon.glyphicon-camera])
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
(defn step-backward []
  [:span.glyphicon.glyphicon-step-backward])
(defn fast-backward []
  [:span.glyphicon.glyphicon-fast-backward])
(defn backward []
  [:span.glyphicon.glyphicon-backward])
(defn play []
  [:span.glyphicon.glyphicon-play])
(defn pause []
  [:span.glyphicon.glyphicon-pause])
(defn stop []
  [:span.glyphicon.glyphicon-stop])
(defn forward []
  [:span.glyphicon.glyphicon-forward])
(defn fast-forward []
  [:span.glyphicon.glyphicon-fast-forward])
(defn step-forward []
  [:span.glyphicon.glyphicon-step-forward])
(defn eject []
  [:span.glyphicon.glyphicon-eject])
(defn plus-sign []
  [:span.glyphicon.glyphicon-plus-sign])
(defn minus-sign []
  [:span.glyphicon.glyphicon-minus-sign])
(defn remove-sign []
  [:span.glyphicon.glyphicon-remove-sign])
(defn ok-sign []
  [:span.glyphicon.glyphicon-ok-sign])
(defn question-sign []
  [:span.glyphicon.glyphicon-question-sign])
(defn screenshot []
  [:span.glyphicon.glyphicon-screenshot])
(defn remove-circle []
  [:span.glyphicon.glyphicon-remove-circle])
(defn ok-circle []
  [:span.glyphicon.glyphicon-ok-circle])
(defn ban-circle []
  [:span.glyphicon.glyphicon-ban-circle])
(defn share-alt []
  [:span.glyphicon.glyphicon-share-alt])
(defn resize-full []
  [:span.glyphicon.glyphicon-resize-full])
(defn resize-small []
  [:span.glyphicon.glyphicon-resize-small])
(defn exclamation-sign []
  [:span.glyphicon.glyphicon-exclamation-sign])
(defn gift []
  [:span.glyphicon.glyphicon-gift])
(defn leaf []
  [:span.glyphicon.glyphicon-leaf])
(defn fire []
  [:span.glyphicon.glyphicon-fire])
(defn warning []
  [:span.glyphicon.glyphicon-warning-sign])
(defn eye-open []
  [:span.glyphicon.glyphicon-eye-open])
(defn eye-close []
  [:span.glyphicon.glyphicon-eye-close])
(defn plane []
  [:span.glyphicon.glyphicon-plane])
(defn calendar []
  [:span.glyphicon.glyphicon-calendar])
(defn random []
  [:span.glyphicon.glyphicon-random])
(defn magnet []
  [:span.glyphicon.glyphicon-magnet])
(defn retweet []
  [:span.glyphicon.glyphicon-retweet])
(defn shopping-cart []
  [:span.glyphicon.glyphicon-shopping-cart])
(defn folder-close []
  [:span.glyphicon.glyphicon-folder-close])
(defn folder-open []
  [:span.glyphicon.glyphicon-folder-open])
(defn resize-vertical []
  [:span.glyphicon.glyphicon-resize-vertical])
(defn resize-horizontal []
  [:span.glyphicon.glyphicon-resize-horizontal])
(defn hdd []
  [:span.glyphicon.glyphicon-hdd])
(defn bullhorn []
  [:span.glyphicon.glyphicon-bullhorn])
(defn bell []
  [:span.glyphicon.glyphicon-bell])
(defn certificate []
  [:span.glyphicon.glyphicon-certificate])
(defn thumbs-up []
  [:span.glyphicon.glyphicon-thumbs-up])
(defn thumbs-down []
  [:span.glyphicon.glyphicon-thumbs-down])
(defn hand-right []
  [:span.glyphicon.glyphicon-hand-right])
(defn hand-left []
  [:span.glyphicon.glyphicon-hand-left])
(defn hand-up []
  [:span.glyphicon.glyphicon-hand-up])
(defn hand-down []
  [:span.glyphicon.glyphicon-hand-down])
(defn circle-arrow-right []
  [:span.glyphicon.glyphicon-circle-arrow-right])
(defn circle-arrow-left []
  [:span.glyphicon.glyphicon-circle-arrow-left])
(defn circle-arrow-up []
  [:span.glyphicon.glyphicon-circle-arrow-up])
(defn circle-arrow-down []
  [:span.glyphicon.glyphicon-circle-arrow-down])
(defn globe []
  [:span.glyphicon.glyphicon-globe])
(defn wrench []
  [:span.glyphicon.glyphicon-wrench])
(defn tasks []
  [:span.glyphicon.glyphicon-tasks])
(defn filter []
  [:span.glyphicon.glyphicon-filter])
(defn briefcase []
  [:span.glyphicon.glyphicon-briefcase])
(defn fullscreen []
  [:span.glyphicon.glyphicon-fullscreen])
(defn dashboard []
  [:span.glyphicon.glyphicon-dashboard])
(defn paperclip []
  [:span.glyphicon.glyphicon-paperclip])
(defn heart-empty []
  [:span.glyphicon.glyphicon-heart-empty])
(defn link []
  [:span.glyphicon.glyphicon-link])
(defn phone []
  [:span.glyphicon.glyphicon-phone])
(defn pushpin []
  [:span.glyphicon.glyphicon-pushpin])
(defn usd []
  [:span.glyphicon.glyphicon-usd])
(defn gbp []
  [:span.glyphicon.glyphicon-gbp])
(defn sort []
  [:span.glyphicon.glyphicon-sort])
(defn sort-by-alphabet []
  [:span.glyphicon.glyphicon-sort-by-alphabet])
(defn sort-by-alphabet-alt []
  [:span.glyphicon.glyphicon-sort-by-alphabet-alt])
(defn sort-by-order []
  [:span.glyphicon.glyphicon-sort-by-order])
(defn sort-by-order-alt []
  [:span.glyphicon.glyphicon-sort-by-order-alt])
(defn sort-by-attributes []
  [:span.glyphicon.glyphicon-sort-by-attributes])
(defn sort-by-attributes-alt []
  [:span.glyphicon.glyphicon-sort-by-attributes-alt])
(defn unchecked []
  [:span.glyphicon.glyphicon-unchecked])
(defn expand []
  [:span.glyphicon.livicon-expand])
(defn collapse-down []
  [:span.glyphicon.glyphicon-collapse-down])
(defn collapse-up []
  [:span.glyphicon.glyphicon-collapse-up])
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
(defn saved []
  [:span.glyphicon.glyphicon-saved])
(defn import-ikoni []
  [:span.glyphicon.glyphicon-import])
(defn export []
  [:span.glyphicon.glyphicon-export])
(defn send []
  [:span.glyphicon.glyphicon-send])
(defn floppy-disk []
  [:span.glyphicon.glyphicon-floppy-disk])
(defn floppy-saved []
  [:span.glyphicon.glyphicon-floppy-saved])
(defn floppy-remove []
  [:span.glyphicon.glyphicon-floppy-remove])
(defn floppy-save []
  [:span.glyphicon.glyphicon-floppy-save])
(defn floppy-open []
  [:span.glyphicon.glyphicon-floppy-open])
(defn credit-card []
  [:span.glyphicon.glyphicon-credit-card])
(defn transfer []
  [:span.glyphicon.glyphicon-transfer])
(defn cutlery []
  [:span.glyphicon.glyphicon-cutlery])
(defn header []
  [:span.glyphicon.glyphicon-header])
(defn compressed []
  [:span.glyphicon.glyphicon-compressed])
(defn compress []
  [:span.glyphicon.livicon-compress])
(defn earphone []
  [:span.glyphicon.glyphicon-earphone])
(defn phone-alt []
  [:span.glyphicon.glyphicon-phone-alt])
(defn tower []
  [:span.glyphicon.glyphicon-tower])
(defn stats []
  [:span.glyphicon.glyphicon-stats])
(defn sd-video []
  [:span.glyphicon.glyphicon-sd-video])
(defn hd-video []
  [:span.glyphicon.glyphicon-hd-video])
(defn subtitles []
  [:span.glyphicon.glyphicon-subtitles])
(defn sound-stereo []
  [:span.glyphicon.glyphicon-sound-stereo])
(defn sound-dolby []
  [:span.glyphicon.glyphicon-sound-dolby])
(defn sound-5-1 []
  [:span.glyphicon.glyphicon-sound-5-1])
(defn sound-6-1 []
  [:span.glyphicon.glyphicon-sound-6-1])
(defn sound-7-1 []
  [:span.glyphicon.glyphicon-sound-7-1])
(defn copyright-mark []
  [:span.glyphicon.glyphicon-copyright-mark])
(defn registration-mark []
  [:span.glyphicon.glyphicon-registration-mark])
(defn cloud-download []
  [:span.glyphicon.glyphicon-cloud-download])
(defn cloud-upload []
  [:span.glyphicon.glyphicon-cloud-upload])
(defn tree-conifer []
  [:span.glyphicon.glyphicon-tree-conifer])
(defn tree-deciduous []
  [:span.glyphicon.glyphicon-tree-deciduous])
(defn cd []
  [:span.glyphicon.glyphicon-cd])
(defn save-file []
  [:span.glyphicon.glyphicon-save-file])
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
(defn equalizer []
  [:span.glyphicon.glyphicon-equalizer])
(defn king []
  [:span.glyphicon.glyphicon-king])
(defn queen []
  [:span.glyphicon.glyphicon-queen])
(defn pawn []
  [:span.glyphicon.glyphicon-pawn])
(defn bishop []
  [:span.glyphicon.glyphicon-bishop])
(defn knight []
  [:span.glyphicon.glyphicon-knight])
(defn baby-formula []
  [:span.glyphicon.glyphicon-baby-formula])
(defn tent []
  [:span.glyphicon.glyphicon-tent])
(defn blackboard []
  [:span.glyphicon.glyphicon-blackboard])
(defn bed []
  [:span.glyphicon.glyphicon-bed])
(defn apple []
  [:span.glyphicon.glyphicon-apple])
(defn erase []
  [:span.glyphicon.glyphicon-erase])
(defn hourglass []
  [:span.glyphicon.glyphicon-hourglass])
(defn lamp []
  [:span.glyphicon.glyphicon-lamp])
(defn duplicate []
  [:span.glyphicon.glyphicon-duplicate])
(defn piggy-bank []
  [:span.glyphicon.glyphicon-piggy-bank])
(defn scissors []
  [:span.glyphicon.glyphicon-scissors])
(defn bitcoin []
  [:span.glyphicon.glyphicon-bitcoin])
(defn yen []
  [:span.glyphicon.glyphicon-yen])
(defn ruble []
  [:span.glyphicon.glyphicon-ruble])
(defn scale []
  [:span.glyphicon.glyphicon-scale])
(defn ice-lolly []
  [:span.glyphicon.glyphicon-ice-lolly])
(defn ice-lolly-tasted []
  [:span.glyphicon.glyphicon-ice-lolly-tasted])
(defn education []
  [:span.glyphicon.glyphicon-education])
(defn option-horizontal []
  [:span.glyphicon.glyphicon-option-horizontal])
(defn option-vertical []
  [:span.glyphicon.glyphicon-option-vertical])
(defn menu-hamburger []
  [:span.glyphicon.glyphicon-menu-hamburger])
(defn modal-window []
  [:span.glyphicon.glyphicon-modal-window])
(defn oil []
  [:span.glyphicon.glyphicon-oil])
(defn grain []
  [:span.glyphicon.glyphicon-grain])
(defn sunglasses []
  [:span.glyphicon.glyphicon-sunglasses])
(defn text-size []
  [:span.glyphicon.glyphicon-text-size])
(defn text-color []
  [:span.glyphicon.glyphicon-text-color])
(defn text-background []
  [:span.glyphicon.glyphicon-text-background])
(defn object-align-top []
  [:span.glyphicon.glyphicon-object-align-top])
(defn object-align-bottom []
  [:span.glyphicon.glyphicon-object-align-bottom])
(defn object-align-horizontal []
  [:span.glyphicon.glyphicon-object-align-horizontal])
(defn object-align-left []
  [:span.glyphicon.glyphicon-object-align-left])
(defn object-align-vertical []
  [:span.glyphicon.glyphicon-object-align-vertical])
(defn object-align-right []
  [:span.glyphicon.glyphicon-object-align-right])
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
(defn superscript []
  [:span.glyphicon.glyphicon-superscript])
(defn subscript []
  [:span.glyphicon.glyphicon-subscript])
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

;; Yksitellen lisätyt harja-ikonit (svg)
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
(defn close-svg []
  [:img {:src "images/harja-icons/navigation/close.svg" :alt "close"}])
(defn copy-lane-svg []
  [:img {:src "images/harja-icons/action/copy-lane.svg" :alt "copy-lane"}])

;; harja-icons -ikonifontti
;; Käytetty icomoon.io-työkalua:
;; - https://icomoon.io/app
;; - yläpalkki: import icons -> dev-resources/images/harja-icons-flat
;; - yläpalkki: select -> maalaa kaikki valituksi
;; - alapalkki: generate font
;; - yläpalkki: preferences
;;   - font name: harja-icons
;;   - class prefix: harja-icon-
;; - alapalkki: download
;; - pura zip
;; - cd harja-icons-v1.0
;; - kopioi fontit
;;   cp fonts/* ~/source/harja/dev-resources/less/fonts
;;   cp fonts/* ~/source/harja/resources/public/fonts
;; - kopioi tyylit (ilman color-määrityksiä) harja-icons.less -tiedostoon
;;   grep -v 'color: #' style.css
;; - päivitä funktiot tähän tiedostoon
;;   - alempana on koodia, jolla voi generoida funktiot replissä

(defn harja-icon-action-add-attachment [] [:span.harja-icon-action-add-attachment])
(defn harja-icon-action-add-picture [] [:span.harja-icon-action-add-picture])
(defn harja-icon-action-add [] [:span.harja-icon-action-add])
(defn harja-icon-action-append [] [:span.harja-icon-action-append])
(defn harja-icon-action-back-circle [] [:span.harja-icon-action-back-circle])
(defn harja-icon-action-clear [] [:span.harja-icon-action-clear])
(defn harja-icon-action-copy-lane [] [:span.harja-icon-action-copy-lane])
(defn harja-icon-action-copy [] [:span.harja-icon-action-copy])
(defn harja-icon-action-current-location [] [:span.harja-icon-action-current-location])
(defn harja-icon-action-decrease-volume [] [:span.harja-icon-action-decrease-volume])
(defn harja-icon-action-delete [] [:span.harja-icon-action-delete])
(defn harja-icon-action-download [] [:span.harja-icon-action-download])
(defn harja-icon-action-edit [] [:span.harja-icon-action-edit])
(defn harja-icon-action-filter [] [:span.harja-icon-action-filter])
(defn harja-icon-action-increase-volume [] [:span.harja-icon-action-increase-volume])
(defn harja-icon-action-log-in [] [:span.harja-icon-action-log-in])
(defn harja-icon-action-log-out [] [:span.harja-icon-action-log-out])
(defn harja-icon-action-message [] [:span.harja-icon-action-message])
(defn harja-icon-action-mute [] [:span.harja-icon-action-mute])
(defn harja-icon-action-new-document [] [:span.harja-icon-action-new-document])
(defn harja-icon-action-phone-answer [] [:span.harja-icon-action-phone-answer])
(defn harja-icon-action-phone-call [] [:span.harja-icon-action-phone-call])
(defn harja-icon-action-pin [] [:span.harja-icon-action-pin])
(defn harja-icon-action-print [] [:span.harja-icon-action-print])
(defn harja-icon-action-redo [] [:span.harja-icon-action-redo])
(defn harja-icon-action-save [] [:span.harja-icon-action-save])
(defn harja-icon-action-search [] [:span.harja-icon-action-search])
(defn harja-icon-action-send-email [] [:span.harja-icon-action-send-email])
(defn harja-icon-action-set-date [] [:span.harja-icon-action-set-date])
(defn harja-icon-action-set-favorite [] [:span.harja-icon-action-set-favorite])
(defn harja-icon-action-set-time [] [:span.harja-icon-action-set-time])
(defn harja-icon-action-show-list [] [:span.harja-icon-action-show-list])
(defn harja-icon-action-show-map [] [:span.harja-icon-action-show-map])
(defn harja-icon-action-show [] [:span.harja-icon-action-show])
(defn harja-icon-action-sort-ascending [] [:span.harja-icon-action-sort-ascending])
(defn harja-icon-action-sort-descending [] [:span.harja-icon-action-sort-descending])
(defn harja-icon-action-sound-on [] [:span.harja-icon-action-sound-on])
(defn harja-icon-action-subtract [] [:span.harja-icon-action-subtract])
(defn harja-icon-action-undo [] [:span.harja-icon-action-undo])
(defn harja-icon-action-upload [] [:span.harja-icon-action-upload])
(defn harja-icon-action-zoom-in [] [:span.harja-icon-action-zoom-in])
(defn harja-icon-action-zoom-out [] [:span.harja-icon-action-zoom-out])
(defn harja-icon-misc-anchor [] [:span.harja-icon-misc-anchor])
(defn harja-icon-misc-bulletin-board [] [:span.harja-icon-misc-bulletin-board])
(defn harja-icon-misc-camera [] [:span.harja-icon-misc-camera])
(defn harja-icon-misc-clock [] [:span.harja-icon-misc-clock])
(defn harja-icon-misc-cone [] [:span.harja-icon-misc-cone])
(defn harja-icon-misc-database [] [:span.harja-icon-misc-database])
(defn harja-icon-misc-digger [] [:span.harja-icon-misc-digger])
(defn harja-icon-misc-document [] [:span.harja-icon-misc-document])
(defn harja-icon-misc-dot-empty [] [:span.harja-icon-misc-dot-empty])
(defn harja-icon-misc-dot-filled [] [:span.harja-icon-misc-dot-filled])
(defn harja-icon-misc-down [] [:span.harja-icon-misc-down])
(defn harja-icon-misc-email [] [:span.harja-icon-misc-email])
(defn harja-icon-misc-file-filled [] [:span.harja-icon-misc-file-filled])
(defn harja-icon-misc-file-info [] [:span.harja-icon-misc-file-info])
(defn harja-icon-misc-file [] [:span.harja-icon-misc-file])
(defn harja-icon-misc-frost-heave [] [:span.harja-icon-misc-frost-heave])
(defn harja-icon-misc-fuel [] [:span.harja-icon-misc-fuel])
(defn harja-icon-misc-graph [] [:span.harja-icon-misc-graph])
(defn harja-icon-misc-layers [] [:span.harja-icon-misc-layers])
(defn harja-icon-misc-maintenance [] [:span.harja-icon-misc-maintenance])
(defn harja-icon-misc-map [] [:span.harja-icon-misc-map])
(defn harja-icon-misc-phone [] [:span.harja-icon-misc-phone])
(defn harja-icon-misc-pin [] [:span.harja-icon-misc-pin])
(defn harja-icon-misc-shelter [] [:span.harja-icon-misc-shelter])
(defn harja-icon-misc-snowflake [] [:span.harja-icon-misc-snowflake])
(defn harja-icon-misc-temperature [] [:span.harja-icon-misc-temperature])
(defn harja-icon-misc-tunnel [] [:span.harja-icon-misc-tunnel])
(defn harja-icon-misc-up [] [:span.harja-icon-misc-up])
(defn harja-icon-misc-windsock [] [:span.harja-icon-misc-windsock])
(defn harja-icon-misc-work-document [] [:span.harja-icon-misc-work-document])
(defn harja-icon-misc-worker [] [:span.harja-icon-misc-worker])
(defn harja-icon-navigation-close [] [:span.harja-icon-navigation-close])
(defn harja-icon-navigation-down [] [:span.harja-icon-navigation-down])
(defn harja-icon-navigation-exit-fullscreen [] [:span.harja-icon-navigation-exit-fullscreen])
(defn harja-icon-navigation-external-link [] [:span.harja-icon-navigation-external-link])
(defn harja-icon-navigation-folder [] [:span.harja-icon-navigation-folder])
(defn harja-icon-navigation-fullscreen [] [:span.harja-icon-navigation-fullscreen])
(defn harja-icon-navigation-home [] [:span.harja-icon-navigation-home])
(defn harja-icon-navigation-left [] [:span.harja-icon-navigation-left])
(defn harja-icon-navigation-menu [] [:span.harja-icon-navigation-menu])
(defn harja-icon-navigation-more [] [:span.harja-icon-navigation-more])
(defn harja-icon-navigation-next-page [] [:span.harja-icon-navigation-next-page])
(defn harja-icon-navigation-notifications [] [:span.harja-icon-navigation-notifications])
(defn harja-icon-navigation-previous-page [] [:span.harja-icon-navigation-previous-page])
(defn harja-icon-navigation-reload [] [:span.harja-icon-navigation-reload])
(defn harja-icon-navigation-right [] [:span.harja-icon-navigation-right])
(defn harja-icon-navigation-settings [] [:span.harja-icon-navigation-settings])
(defn harja-icon-navigation-submenu [] [:span.harja-icon-navigation-submenu])
(defn harja-icon-navigation-up [] [:span.harja-icon-navigation-up])
(defn harja-icon-navigation-user [] [:span.harja-icon-navigation-user])
(defn harja-icon-road-bus [] [:span.harja-icon-road-bus])
(defn harja-icon-road-coat-sand-denied [] [:span.harja-icon-road-coat-sand-denied])
(defn harja-icon-road-coat-sand [] [:span.harja-icon-road-coat-sand])
(defn harja-icon-road-coat-snow [] [:span.harja-icon-road-coat-snow])
(defn harja-icon-road-fissure [] [:span.harja-icon-road-fissure])
(defn harja-icon-road-road [] [:span.harja-icon-road-road])
(defn harja-icon-road-snow-plow [] [:span.harja-icon-road-snow-plow])
(defn harja-icon-status-alert [] [:span.harja-icon-status-alert])
(defn harja-icon-status-completed [] [:span.harja-icon-status-completed])
(defn harja-icon-status-denied [] [:span.harja-icon-status-denied])
(defn harja-icon-status-downward [] [:span.harja-icon-status-downward])
(defn harja-icon-status-error [] [:span.harja-icon-status-error])
(defn harja-icon-status-help [] [:span.harja-icon-status-help])
(defn harja-icon-status-info [] [:span.harja-icon-status-info])
(defn harja-icon-status-locked [] [:span.harja-icon-status-locked])
(defn harja-icon-status-selected [] [:span.harja-icon-status-selected])
(defn harja-icon-status-upward [] [:span.harja-icon-status-upward])

;; harja-icon -funktioiden generointiin on käytetty tätä koodia
(comment
  (def harja-icons
    ["action-add-attachment"
     "action-add-picture"
     "action-add"
     "action-append"
     "action-back-circle"
     "action-clear"
     "action-copy-lane"
     "action-copy"
     "action-current-location"
     "action-decrease-volume"
     "action-delete"
     "action-download"
     "action-edit"
     "action-filter"
     "action-increase-volume"
     "action-log-in"
     "action-log-out"
     "action-message"
     "action-mute"
     "action-new-document"
     "action-phone-answer"
     "action-phone-call"
     "action-pin"
     "action-print"
     "action-redo"
     "action-save"
     "action-search"
     "action-send-email"
     "action-set-date"
     "action-set-favorite"
     "action-set-time"
     "action-show-list"
     "action-show-map"
     "action-show"
     "action-sort-ascending"
     "action-sort-descending"
     "action-sound-on"
     "action-subtract"
     "action-undo"
     "action-upload"
     "action-zoom-in"
     "action-zoom-out"
     "misc-anchor"
     "misc-bulletin-board"
     "misc-camera"
     "misc-clock"
     "misc-cone"
     "misc-database"
     "misc-digger"
     "misc-document"
     "misc-dot-empty"
     "misc-dot-filled"
     "misc-down"
     "misc-email"
     "misc-file-filled"
     "misc-file-info"
     "misc-file"
     "misc-frost-heave"
     "misc-fuel"
     "misc-graph"
     "misc-layers"
     "misc-maintenance"
     "misc-map"
     "misc-phone"
     "misc-pin"
     "misc-shelter"
     "misc-snowflake"
     "misc-temperature"
     "misc-tunnel"
     "misc-up"
     "misc-windsock"
     "misc-work-document"
     "misc-worker"
     "navigation-close"
     "navigation-down"
     "navigation-exit-fullscreen"
     "navigation-external-link"
     "navigation-folder"
     "navigation-fullscreen"
     "navigation-home"
     "navigation-left"
     "navigation-menu"
     "navigation-more"
     "navigation-next-page"
     "navigation-notifications"
     "navigation-previous-page"
     "navigation-reload"
     "navigation-right"
     "navigation-settings"
     "navigation-submenu"
     "navigation-up"
     "navigation-user"
     "road-bus"
     "road-coat-sand-denied"
     "road-coat-sand"
     "road-coat-snow"
     "road-fissure"
     "road-road"
     "road-snow-plow"
     "status-alert"
     "status-completed"
     "status-denied"
     "status-downward"
     "status-error"
     "status-help"
     "status-info"
     "status-locked"
     "status-selected"
     "status-upward"])

  (defn ikoni->funktio [ikoni-str]
    (str "(defn harja-icon-" ikoni-str " [] [:span.harja-icon-" ikoni-str "])"))

  (->> harja-icons
       (map ikoni->funktio)
       (str/join "\n")))

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