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
(defn tee-sittenkin []
  [:span.livicon-rotate-right])
(defn tallenna []
  [:span.livicon-check])
(defn livicon-kommentti []
  [:span.livicon-comment])

(defn klikattava-roskis [toiminto]
  [:span.klikattava
   {:on-click toiminto}
   (livicon-trash)])

;; Ikoni + jotain muuta -tyyppiset apurit

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
