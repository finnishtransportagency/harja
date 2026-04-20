(ns harja.ui.tom
  (:require [reagent.core :as r]
            [reagent.dom :as rdom]
            [goog.object :as gobj]))


(defn call-method [obj method & args]
  (.apply (gobj/get obj method) obj (to-array args)))


(defn tom-select [attrs & children]
  (let [Core
        (r/create-class
          {:display-name "tom-select-core"
           :component-did-mount
           (fn [this]
             (when js/TomSelect
               (let [el (rdom/dom-node this)
                     p (r/props this)
                     inst (js/TomSelect. el (or (:ts/options p) #js {}))
                     reposition (fn []
                                  (let [dp (gobj/get inst "dropdown")
                                        ctl (gobj/get inst "control")
                                        r (.getBoundingClientRect ctl)]
                                    (set! (.. dp -style -position) "fixed")
                                    (set! (.. dp -style -left) (str (.-left r) "px"))
                                    (set! (.. dp -style -top) (str (.-bottom r) "px"))
                                    (set! (.. dp -style -width) (str (.-width r) "px"))))]
                 (gobj/set this "_ts" inst)
                 (call-method inst "on" "dropdown_open"
                   (fn []
                     (reposition)
                     (.addEventListener js/window "resize" reposition)))
                 (call-method inst "on" "dropdown_close"
                   (fn []
                     (.removeEventListener js/window "resize" reposition)))
                 (when-let [v (:value p)]
                   (call-method inst "setValue" (clj->js v) true)))))

           :component-did-update
           (fn [this _]
             (when-let [inst (gobj/get this "_ts")]
               (when-let [v (:value (r/props this))]
                 (call-method inst "setValue" (clj->js v) true))))

           :component-will-unmount
           (fn [this]
             (when-let [inst (gobj/get this "_ts")]
               (call-method inst "destroy")))

           :reagent-render
           (fn [attrs & props]
             (into [:select.form-select (dissoc attrs :ts/options :value)] props))})]
    (into [Core attrs] children)))


(defn- esimerkki []
  [tom-select
   {:id "select-tags"
    :multiple true
    :placeholder "Select tags"
    :data-bs-toggle "select"
    :ts/options #js {:copyClassesToDropdown false
                     :dropdownParent "body"
                     :controlInput "<input>"}}
   [:option {:value "HTML"} "HTML"]
   [:option {:value "JavaScript"} "JavaScript"]
   [:option {:value "CSS"} "CSS"]
   [:option {:value "jQuery"} "jQuery"]
   [:option {:value "Bootstrap"} "Bootstrap"]
   [:option {:value "Ruby"} "Ruby"]
   [:option {:value "Python"} "Python"]])
