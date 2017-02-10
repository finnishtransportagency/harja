(ns harja-laadunseuranta.test-macros)

(defmacro prepare-component-tests []
  `(cljs.test/use-fixtures :each
     {:before (fn [] (reset! harja.testutils.shared-testutils/*test-container* (cljs-react-test.utils/new-container!)))
      :after (fn [] (cljs-react-test.utils/unmount! @harja.testutils.shared-testutils/*test-container*))}))

(defmacro with-component [component & body]
  `(do
     (reagent.core/render ~component @harja.testutils.shared-testutils/*test-container*)
     ~@body))