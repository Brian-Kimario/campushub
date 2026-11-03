import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { Provider } from 'react-redux'
import { RouterProvider } from 'react-router'
import { router } from './app/router'
import { SessionGate } from './app/SessionGate'
import { store } from './app/store'
import './index.css'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <Provider store={store}>
      <SessionGate>
        <RouterProvider router={router} />
      </SessionGate>
    </Provider>
  </StrictMode>,
)
