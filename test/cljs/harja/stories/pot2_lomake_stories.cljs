(ns harja.stories.pot2-lomake-stories
  (:require
    [reagent.core :refer [atom] :as r]
    [harja.views.urakka.pot2.pot2-lomake :refer [pot2-lomake blabla]]))

(def ^:export default
  #js {:title     "POT2 lomake"
       :component (r/reactify-component blabla)})

(defn ^:export HelloWorldHeader []
  (r/as-element [blabla "Hello, World!"]))